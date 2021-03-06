/*
 * Copyright 2016 Karlsruhe Institute of Technology (KIT)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.kit.scc.backend;

import org.indigo.cdmi.BackEndException;
import org.indigo.cdmi.BackendCapability;
import org.indigo.cdmi.BackendCapability.CapabilityType;
import org.indigo.cdmi.CdmiObjectStatus;
import org.indigo.cdmi.spi.StorageBackend;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class FilesystemBackend implements StorageBackend {

  private static final Logger log = LoggerFactory.getLogger(FilesystemBackend.class);

  // simulates back-end capabilities
  private ArrayList<BackendCapability> backendCapabilities = new ArrayList<>();

  // simulates monitored attributes for containers
  private HashMap<String, Map<String, Object>> containerMonitoredAttributes = new HashMap<>();

  // simulates monitored attributes for dataobjects
  private HashMap<String, Map<String, Object>> dataobjectMonitoredAttributes = new HashMap<>();

  // simulates QoS support
  private Map<String, CdmiObjectStatus> objectMap = Collections.synchronizedMap(new HashMap<>());

  private Map<String, String> properties;

  // startup timestamp
  private String startupTime;

  private String defaultContainerCapabilityClass;
  private String defaultDataobjectCapabilityClass;

  private JSONObject exports;

  /**
   * Reads capabilities from a JSON configuration file.
   * 
   * @return a {@link JSONObject} with the back-end's capabilities
   */
  public JSONObject readCapabilitiesFromConfig() {
    JSONObject json = new JSONObject();

    try {
      InputStream in = new ClassPathResource("filesystem-capabilities.json").getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(in));

      StringBuffer stringBuffer = new StringBuffer();
      String inputLine;
      while ((inputLine = reader.readLine()) != null) {
        stringBuffer.append(inputLine);
      }

      json = new JSONObject(stringBuffer.toString());

      log.debug("Capabilities config {}", json.toString());

    } catch (IOException ex) {
      ex.printStackTrace();
    }
    return json;
  }

  /**
   * Constructs a dummy file-system module with dummy values.
   * 
   * @param properties file-system properties
   */
  public FilesystemBackend(Map<String, String> properties) {
    this.properties = properties;

    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    df.setTimeZone(tz);
    this.startupTime = df.format(new Date());

    JSONObject capabilities = readCapabilitiesFromConfig();

    exports = capabilities.getJSONObject("container_exports");

    defaultContainerCapabilityClass = capabilities.getString("default_container_capability_class");
    defaultDataobjectCapabilityClass =
        capabilities.getString("default_dataobject_capability_class");

    Map<String, Object> containerCapabilities = new HashMap<>();
    JSONObject jsonCapabilities = capabilities.getJSONObject("container_capabilities");
    for (String key : jsonCapabilities.keySet()) {
      containerCapabilities.put(key, jsonCapabilities.get(key));
    }
    JSONObject containerClasses = capabilities.getJSONObject("container_classes");
    for (String key : containerClasses.keySet()) {
      JSONObject containerClass = containerClasses.getJSONObject(key);
      log.debug("found {} capabilities class {}: {}", CapabilityType.CONTAINER, key,
          containerClass.toString());

      BackendCapability backendCapability = new BackendCapability(key, CapabilityType.CONTAINER);

      Map<String, Object> metadata = new HashMap<>();
      Map<String, Object> monitored = new HashMap<>();
      for (String capability : containerClass.keySet()) {
        metadata.put(capability, containerClass.get(capability));
        // TODO clarify _provided suffix for capabilities
        if (capability.equals("cdmi_capabilities_allowed")) {
          // monitored.put(capability, containerClass.get(capability));
          monitored.put(capability + "_provided", containerClass.get(capability));
        } else {
          // monitored.put(capability, containerClass.get(capability));
          monitored.put(capability + "_provided", containerClass.get(capability));
        }
      }

      containerMonitoredAttributes.put(key, monitored);
      backendCapability.setMetadata(metadata);
      backendCapability.setCapabilities(containerCapabilities);

      backendCapabilities.add(backendCapability);
    }

    Map<String, Object> dataObjectCapabilities = new HashMap<>();
    jsonCapabilities = capabilities.getJSONObject("dataobject_capabilities");
    for (String key : jsonCapabilities.keySet()) {
      dataObjectCapabilities.put(key, jsonCapabilities.get(key));
    }

    JSONObject dataObjectClasses = capabilities.getJSONObject("dataobject_classes");
    for (String key : dataObjectClasses.keySet()) {
      JSONObject dataObjectClass = dataObjectClasses.getJSONObject(key);
      log.debug("found {} capabilities class {}: {}", CapabilityType.DATAOBJECT, key,
          dataObjectClass.toString());

      BackendCapability backendCapability = new BackendCapability(key, CapabilityType.DATAOBJECT);

      Map<String, Object> metadata = new HashMap<>();
      Map<String, Object> monitored = new HashMap<>();
      for (String capability : dataObjectClass.keySet()) {
        metadata.put(capability, dataObjectClass.get(capability));
        // TODO clarify _provided suffix for capabilities
        if (capability.equals("cdmi_capabilities_allowed")) {
          // monitored.put(capability, dataObjectClass.get(capability));
          monitored.put(capability + "_provided", dataObjectClass.get(capability));
        } else {
          // monitored.put(capability, dataObjectClass.get(capability));
          monitored.put(capability + "_provided", dataObjectClass.get(capability));
        }
      }

      dataobjectMonitoredAttributes.put(key, monitored);
      backendCapability.setMetadata(metadata);
      backendCapability.setCapabilities(dataObjectCapabilities);

      backendCapabilities.add(backendCapability);
    }
  }

  private boolean isSupportedTargetCapabilitiesUri(String path, String capabilitiesUri) {
    try {
      CdmiObjectStatus objectStatus = getCurrentStatus(path);
      String[] strArr = objectStatus.getCurrentCapabilitiesUri().split("/");
      String capabilitiesType = strArr[strArr.length - 2];
      String capabilitiesName = strArr[strArr.length - 1];

      BackendCapability currentCapability = null;

      if (capabilitiesType.equals("container")) {
        currentCapability =
            backendCapabilities.stream().filter(b -> b.getType().equals(CapabilityType.CONTAINER)
                && b.getName().equals(capabilitiesName)).findFirst().get();
      } else if (capabilitiesType.equals("dataobject")) {
        currentCapability =
            backendCapabilities.stream().filter(b -> b.getType().equals(CapabilityType.DATAOBJECT)
                && b.getName().equals(capabilitiesName)).findFirst().get();
      }

      if (currentCapability != null) {
        if (currentCapability.getMetadata().containsKey("cdmi_capabilities_allowed")) {
          JSONArray allowedCapabilities =
              (JSONArray) currentCapability.getMetadata().get("cdmi_capabilities_allowed");
          for (Object obj : allowedCapabilities) {
            if (String.valueOf(obj).equals(capabilitiesUri)) {
              return true;
            }
          }
        }
      } else {
        log.warn("Could not get capabilities for {}", capabilitiesUri);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      log.warn("ERROR {}", ex.getMessage());
    }
    log.warn("target capabilities URI not supported {}", capabilitiesUri);
    return false;
  }

  @Override
  public List<BackendCapability> getCapabilities() {
    return backendCapabilities;
  }

  /**
   * Get simulated monitoring attributes for containers and databobjects.
   * 
   * @param capabilitiesUri capabilities URI to lookup according attributes.
   * @return the monitoring attributes
   */
  public Map<String, Object> getMonitoredAttributes(String capabilitiesUri) {
    String[] strArr = capabilitiesUri.split("/");
    String capabilitiesType = strArr[strArr.length - 2];
    String capabilitiesName = strArr[strArr.length - 1];

    log.debug("lookup capabilities {} {} for {}", capabilitiesType, capabilitiesName,
        capabilitiesUri);

    if (capabilitiesType.equals("container")) {
      if (containerMonitoredAttributes.containsKey(capabilitiesName)) {
        return containerMonitoredAttributes.get(capabilitiesName);
      } else {
        log.warn("Unknown capabilities name {}", capabilitiesName);
      }
    } else if (capabilitiesType.equals("dataobject")) {
      if (dataobjectMonitoredAttributes.containsKey(capabilitiesName)) {
        return dataobjectMonitoredAttributes.get(capabilitiesName);
      } else {
        log.warn("Unknown capabilities name {}", capabilitiesName);
      }
    } else {
      log.warn("Invalid capabilities type {}", capabilitiesType);
    }
    return new HashMap<>();
  }

  @Override
  public void updateCdmiObject(String path, String targetCapabilitiesUri) throws BackEndException {
    if (!isSupportedTargetCapabilitiesUri(path, targetCapabilitiesUri)) {
      throw new BackEndException();
    }

    CdmiObjectStatus objectStatus = getCurrentStatus(path);

    if (objectStatus.getTargetCapabilitiesUri() != null) {
      log.debug("object {} already in transition to {}", path,
          objectStatus.getTargetCapabilitiesUri());
    } else {
      log.debug("current object status {}", objectStatus.toString());

      String currentCapabilitiesUri = objectStatus.getCurrentCapabilitiesUri();
      log.debug("Simulate QoS transition for {} from {} to {}", path, currentCapabilitiesUri,
          targetCapabilitiesUri);

      Map<String, Object> monitoredAttributes = getMonitoredAttributes(currentCapabilitiesUri);
      Map<String, Object> metadata = new HashMap<>();
      for (Entry<String, Object> entry : monitoredAttributes.entrySet()) {
        metadata.put(entry.getKey(), entry.getValue());
      }
      metadata.put("cdmi_capabilities_target", targetCapabilitiesUri);
      metadata.put("cdmi_recommended_polling_interval", "10000");

      objectMap.put(path,
          new CdmiObjectStatus(metadata, currentCapabilitiesUri, targetCapabilitiesUri));

      log.debug("---- Updated status HashMap dump ----");
      log.debug("{}", objectMap.toString());

      // simulates a 10 sec transition
      long delay = 10 * 1000;
      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          log.debug("Simulated QoS transition for {} from {} to {} finished", path,
              currentCapabilitiesUri, targetCapabilitiesUri);
          Map<String, Object> monitoredAttributes = getMonitoredAttributes(targetCapabilitiesUri);
          Map<String, Object> metadata = new HashMap<>();
          for (Entry<String, Object> entry : monitoredAttributes.entrySet()) {
            metadata.put(entry.getKey(), entry.getValue());
          }

          TimeZone tz = TimeZone.getTimeZone("UTC");
          DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
          df.setTimeZone(tz);
          String now = df.format(new Date());
          metadata.put("cdmi_capability_association_time", now);

          objectMap.put(path, new CdmiObjectStatus(metadata, targetCapabilitiesUri, null));
        }
      }, delay);
    }
  }

  private Path getFileSystemPath(String path) throws BackEndException {
    try {
      if (properties.containsKey("baseDirectory")) {
        String baseDirectory = properties.get("baseDirectory");
        log.debug("Base directory {}", baseDirectory);

        Path returnPath = Paths.get(baseDirectory, path);
        log.debug("Filesystem path {}", returnPath.toString());

        return returnPath;
      } else {
        throw new BackEndException("Base directory path is missing.");
      }
    } catch (NullPointerException | InvalidPathException ex) {
      throw new BackEndException(ex.getMessage());
    }
  }

  private List<String> listFileSystemDir(String path) {
    try {
      Path filesystemPath = getFileSystemPath(path);

      return Arrays.asList(filesystemPath.toFile().list());
    } catch (BackEndException ex) {
      log.warn("Can't get directory listing for {}", path);
    }
    return new ArrayList<>();
  }

  @Override
  public CdmiObjectStatus getCurrentStatus(String path) throws BackEndException {

    if (!Files.exists(getFileSystemPath(path))) {
      throw new BackEndException("no such file");
    }

    if (!objectMap.containsKey(path)) {
      String capabilitiesUri = "";

      String defaultDataobjectCapabilities = null;
      HashMap<String, Object> exportAttributes = null;
      List<String> children = null;

      if (Files.isDirectory(getFileSystemPath(path))) {
        capabilitiesUri = defaultContainerCapabilityClass;
        defaultDataobjectCapabilities = defaultDataobjectCapabilityClass;

        exportAttributes = new HashMap<>();
        for (String key : exports.keySet()) {
          exportAttributes.put(key, exports.get(key));
        }

        children = listFileSystemDir(path);

      } else {
        capabilitiesUri = defaultDataobjectCapabilityClass;
      }

      Map<String, Object> monitoredAttributes = getMonitoredAttributes(capabilitiesUri);
      Map<String, Object> metadata = new HashMap<>();

      for (Entry<String, Object> entry : monitoredAttributes.entrySet()) {
        metadata.put(entry.getKey(), entry.getValue());
      }
      metadata.put("cdmi_capability_association_time", startupTime);

      if (defaultDataobjectCapabilities != null) {
        metadata.put("cdmi_default_dataobject_capability class", defaultDataobjectCapabilities);
      }

      CdmiObjectStatus status = new CdmiObjectStatus(metadata, capabilitiesUri, null);

      if (exportAttributes != null) {
        status.setExportAttributes(exportAttributes);
      }
      if (children != null) {
        status.setChildren(children);
      }

      objectMap.put(path, status);
    } else {
      CdmiObjectStatus status = objectMap.get(path);

      // update children
      if (Files.isDirectory(getFileSystemPath(path))) {
        List<String> children = listFileSystemDir(path);
        status.setChildren(children);
      }

      objectMap.put(path, status);
    }

    log.debug("---- Get current status HashMap dump ----");
    log.debug("{}", objectMap.toString());

    return objectMap.get(path);
  }

}
