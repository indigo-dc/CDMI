/*
 * Copyright 2016 Karlsruhe Institute of Technology (KIT)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.kit.scc.rest;

import edu.kit.scc.CdmiRestController;
import edu.kit.scc.CdmiServerApplication;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.servlet.HandlerMapping;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = CdmiServerApplication.class)
public class ContainerTest {

  private static final Logger log = LoggerFactory.getLogger(ContainerTest.class);

  @Autowired
  private CdmiRestController controller;

  @Test
  public void A_create() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setServerName("localhost:8080");
    request.setRequestURI("/containerTest/");
    request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/containerTest/");
    request.addHeader("Accept", "application/cdmi-container");
    request.addHeader("Content-Type", "application/cdmi-container");
    request.setContent(
        "{ \"value\":{}, \"metadata\" : { created: by test, color:yellow } }".getBytes());
    request.setMethod("PUT");


    ResponseEntity<?> res = controller.putCdmiObject(request.getContentType(),
        "{ \"value\":{}, \"metadata\" : { created: by test, color:yellow } }", request, response);

    log.debug("Create container {}", res.getStatusCode());
    if (!res.getStatusCode().equals(HttpStatus.OK)) {
      log.debug("ERROR reason {}", res.getBody());
    }
  }

  @Test
  public void B_get() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setServerName("localhost:8080");
    request.setRequestURI("/containerTest/");
    request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/containerTest/");
    request.setMethod("GET");


    ResponseEntity<?> res = controller.getCdmiObjectByPath(request, response);
    String content = (String) res.getBody();
    String objectId = content.split("objectID\":\"")[1].split("\"")[0];

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    request.setServerName("localhost:8080");
    request.setRequestURI("/cdmi_objectid/" + objectId);
    request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
        "/cdmi_objectid/" + objectId);
    request.setMethod("GET");
    controller.getCdmiObjectByID(objectId, request, response);
  }

  @Test
  public void C_get_fields() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setServerName("localhost:8080");
    request.setRequestURI("/containerTest/?metadata:color");
    request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/containerTest/");
    request.setParameter("metadata:color", "");
    request.setMethod("GET");


    controller.getCdmiObjectByPath(request, response);

  }

  @Test
  public void D_delete() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setServerName("localhost:8080");
    request.setRequestURI("/containerTest/");
    request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/containerTest/");
    request.addHeader("Content-Type", "application/cdmi-container");
    request.setMethod("DELETE");


    controller.deleteCdmiObject(request, response);

  }


}