/*
 * Copyright 2016 Karlsruhe Institute of Technology (KIT)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.kit.scc.redis;

import org.indigo.cdmi.BackEndException;
import org.indigo.cdmi.BackendCapability;
import org.indigo.cdmi.BackendCapability.CapabilityType;
import org.indigo.cdmi.ConfigurableStorageBackend;
import org.indigo.cdmi.spi.StorageBackend;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snia.cdmiserver.dao.CapabilityDao;
import org.snia.cdmiserver.dao.CdmiObjectDao;
import org.snia.cdmiserver.model.Capability;
import org.snia.cdmiserver.model.CdmiObject;
import org.snia.cdmiserver.model.Container;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;

@Component
@Profile({"redis"})
public class RedisConfiguration {

  private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);

  @Autowired
  private CdmiObjectDao cdmiObjectDao;

  @Autowired
  private CapabilityDao capabilityDao;

  @Value("${cdmi.data.baseDirectory}")
  private String baseDirectory;

  @Value("${cdmi.qos.backend.type}")
  private String backendType;

  /**
   * Configuration for CDMI redis version.
   * 
   */
  @PostConstruct
  public void init() {
    log.debug("Set-up root container...");

    CdmiObject rootObject = new CdmiObject();
    Container rootContainer = new Container("/", "/", rootObject.getObjectId());
    rootContainer.setObjectId(rootObject.getObjectId());

    rootContainer = (Container) cdmiObjectDao.createCdmiObject(rootContainer, "/");
    log.debug("root container created {}", rootContainer.toString());

    rootObject = cdmiObjectDao.getCdmiObjectByPath("/");

    Capability rootCapability = new Capability("cdmi_capabilities", "/", rootObject.getObjectId());
    rootCapability =
        (Capability) cdmiObjectDao.createCdmiObject(rootCapability, "/cdmi_capabilities");
    log.debug("root capability created {}", rootCapability.toString());

    Capability containerCapability =
        new Capability("container", "/cdmi_capabilities", rootCapability.getObjectId());
    capabilityDao.createByPath(Paths.get("/cdmi_capabilities", "container").toString(),
        containerCapability);

    Capability dataObjectCapability =
        new Capability("dataobject", "/cdmi_capabilities", rootCapability.getObjectId());
    capabilityDao.createByPath(Paths.get("/cdmi_capabilities", "dataobject").toString(),
        dataObjectCapability);

    Capability defaultContainerCapability =
        capabilityDao.findByPath(Paths.get("/cdmi_capabilities", "container").toString());
    log.debug(defaultContainerCapability.toString());

    Capability defaultDataObjectCapability =
        capabilityDao.findByPath(Paths.get("/cdmi_capabilities", "dataobject").toString());
    log.debug(defaultDataObjectCapability.toString());

    // Connect to a specific file system storage back-end implementation.
    //
    // Creates the provided for this specific storage back-end capabilities.
    HashMap<String, String> backendProperties = new HashMap<String, String>();
    backendProperties.put("baseDirectory", baseDirectory);

    try {
      StorageBackend storageBackend =
          ConfigurableStorageBackend.createStorageBackend(backendType, backendProperties);

      List<BackendCapability> capabilities = storageBackend.getCapabilities();
      for (BackendCapability capability : capabilities) {
        log.debug("Found capability type {} {}", capability.getType().name(), capability.getName());

        if (capability.getType().equals(CapabilityType.CONTAINER)) {
          Capability providedCapability = new Capability(capability.getName(),
              "/cdmi_capabilities/container", defaultContainerCapability.getObjectId());
          providedCapability.setCapabilities(new JSONObject(capability.getCapabilities()));
          providedCapability.setMetadata(new JSONObject(capability.getMetadata()));
          capabilityDao.createByPath(
              Paths.get("/", "cdmi_capabilities", "container", capability.getName()).toString(),
              providedCapability);
        }
        if (capability.getType().equals(CapabilityType.DATAOBJECT)) {
          Capability providedCapability = new Capability(capability.getName(),
              "/cdmi_capabilities/dataobject", defaultContainerCapability.getObjectId());
          providedCapability.setCapabilities(new JSONObject(capability.getCapabilities()));
          providedCapability.setMetadata(new JSONObject(capability.getMetadata()));
          capabilityDao.createByPath(
              Paths.get("/", "cdmi_capabilities", "dataobject", capability.getName()).toString(),
              providedCapability);
        }
      }
    } catch (IllegalArgumentException | BackEndException ex) {
      log.warn("ERROR: {}", ex.getMessage());
    }
  }
}
