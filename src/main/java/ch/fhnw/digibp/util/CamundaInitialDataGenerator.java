/*
 * Copyright (c) 2017. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.fhnw.digibp.util;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;

import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Permissions;

import static org.camunda.bpm.engine.authorization.Permissions.ACCESS;
import static org.camunda.bpm.engine.authorization.Permissions.ALL;
import static org.camunda.bpm.engine.authorization.Permissions.READ;

import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;

import static org.camunda.bpm.engine.authorization.Resources.APPLICATION;
import static org.camunda.bpm.engine.authorization.Resources.FILTER;

import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.camunda.bpm.engine.task.TaskQuery;
import org.springframework.stereotype.Component;

/**
 * @author andreas.martin
 */
@Component
public class CamundaInitialDataGenerator {

    @Inject
    private ProcessEngine processEngine;

    private final static Logger LOGGER = Logger.getLogger(CamundaInitialDataGenerator.class.getName());

    @PostConstruct
    public void createUsers() {
        final IdentityService identityService = processEngine.getIdentityService();

        if (identityService.isReadOnly()) {
            LOGGER.info("Identity service provider is Read Only, not creating any demo users.");
            return;
        }

        if (identityService.createUserQuery().userId("demo").singleResult() == null) {
            LOGGER.info("Generating demo user for MSc BIS");
            User user = identityService.newUser("demo");
            user.setFirstName("Demo");
            user.setLastName("Demo");
            user.setPassword("demo");
            user.setEmail("demo@camunda.org");
            identityService.saveUser(user);
        }

        final AuthorizationService authorizationService = processEngine.getAuthorizationService();

        // create group
        if (identityService.createGroupQuery().groupId(Groups.CAMUNDA_ADMIN).singleResult() == null) {
            Group camundaAdminGroup = identityService.newGroup(Groups.CAMUNDA_ADMIN);
            camundaAdminGroup.setName("camunda BPM Administrators");
            camundaAdminGroup.setType(Groups.GROUP_TYPE_SYSTEM);
            identityService.saveGroup(camundaAdminGroup);


            // create ADMIN authorizations on all built-in resources
            for (Resource resource : Resources.values()) {
                if (authorizationService.createAuthorizationQuery().groupIdIn(Groups.CAMUNDA_ADMIN).resourceType(resource).resourceId(ANY).count() == 0) {
                    AuthorizationEntity userAdminAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
                    userAdminAuth.setGroupId(Groups.CAMUNDA_ADMIN);
                    userAdminAuth.setResource(resource);
                    userAdminAuth.setResourceId(ANY);
                    userAdminAuth.addPermission(ALL);
                    authorizationService.saveAuthorization(userAdminAuth);
                }
            }
        }

        if (identityService.createUserQuery().userId("demo").memberOfGroup("camunda-admin").singleResult() == null) {
            identityService.createMembership("demo", "camunda-admin");
        }

        // create default filters
        FilterService filterService = processEngine.getFilterService();

        if (filterService.createFilterQuery().filterName("My Tasks").singleResult() == null) {
            LOGGER.info("Generating default filters");
            Map<String, Object> filterProperties = new HashMap<String, Object>();
            filterProperties.put("description", "Tasks assigned to me");
            filterProperties.put("priority", -10);
            filterProperties.put("refresh", true);
            TaskService taskService = processEngine.getTaskService();
            TaskQuery query = taskService.createTaskQuery().taskAssigneeExpression("${currentUser()}");
            Filter myTasksFilter = filterService.newTaskFilter().setName("My Tasks").setProperties(filterProperties).setOwner("demo").setQuery(query);
            filterService.saveFilter(myTasksFilter);

            filterProperties.clear();
            filterProperties.put("description", "Tasks assigned to my Groups");
            filterProperties.put("priority", -5);
            filterProperties.put("refresh", true);
            query = taskService.createTaskQuery().taskCandidateGroupInExpression("${currentUserGroups()}").taskUnassigned();
            Filter groupTasksFilter = filterService.newTaskFilter().setName("My Group Tasks").setProperties(filterProperties).setOwner("demo").setQuery(query);
            filterService.saveFilter(groupTasksFilter);

            // global read authorizations for these filters
            Authorization globalMyTaskFilterRead = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GLOBAL);
            globalMyTaskFilterRead.setResource(FILTER);
            globalMyTaskFilterRead.setResourceId(myTasksFilter.getId());
            globalMyTaskFilterRead.addPermission(READ);
            authorizationService.saveAuthorization(globalMyTaskFilterRead);

            Authorization globalGroupFilterRead = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GLOBAL);
            globalGroupFilterRead.setResource(FILTER);
            globalGroupFilterRead.setResourceId(groupTasksFilter.getId());
            globalGroupFilterRead.addPermission(READ);
            authorizationService.saveAuthorization(globalGroupFilterRead);
        }
    }

}
