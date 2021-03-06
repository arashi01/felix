/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.http.base.internal.runtime.dto;

import static java.util.Arrays.asList;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.sort;
import static java.util.Collections.emptyMap;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.ID_COUNTER;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createContextInfo;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createErrorPage;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createErrorPageWithServiceId;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createFilterInfo;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createServletInfo;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createTestFilter;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createTestFilterWithServiceId;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createTestServlet;
import static org.apache.felix.http.base.internal.runtime.WhiteboardServiceHelper.createTestServletWithServiceId;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;

import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.FilterHandler;
import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.runtime.AbstractInfo;
import org.apache.felix.http.base.internal.runtime.FilterInfo;
import org.apache.felix.http.base.internal.runtime.ServletContextHelperInfo;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.apache.felix.http.base.internal.whiteboard.ContextHandler;
import org.apache.felix.http.base.internal.whiteboard.ListenerRegistry;
import org.apache.felix.http.base.internal.whiteboard.PerContextEventListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.dto.DTO;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.runtime.dto.ErrorPageDTO;
import org.osgi.service.http.runtime.dto.FilterDTO;
import org.osgi.service.http.runtime.dto.ListenerDTO;
import org.osgi.service.http.runtime.dto.ResourceDTO;
import org.osgi.service.http.runtime.dto.RuntimeDTO;
import org.osgi.service.http.runtime.dto.ServletContextDTO;
import org.osgi.service.http.runtime.dto.ServletDTO;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;

@RunWith(MockitoJUnitRunner.class)
public class RuntimeDTOBuilderTest
{
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final Long ID_0 = -ID_COUNTER.incrementAndGet();
    private static final Long ID_A = ID_COUNTER.incrementAndGet();
    private static final Long ID_B = ID_COUNTER.incrementAndGet();
    private static final Long ID_C = ID_COUNTER.incrementAndGet();
    private static final Long ID_D = ID_COUNTER.incrementAndGet();

    private static final Long ID_LISTENER_1 = ID_COUNTER.incrementAndGet();
    private static final Long ID_LISTENER_2 = ID_COUNTER.incrementAndGet();

    private static final List<String> CONTEXT_NAMES = Arrays.asList("0", "A", "B");

    @SuppressWarnings("serial")
    private static final Map<String, List<String>> CONTEXT_ENTITY_NAMES = new HashMap<String, List<String>>()
            {
                {
                    put("0", Arrays.asList("1"));
                    put("A", Arrays.asList("A_1"));
                    put("B", Arrays.asList("B_1", "B_2"));
                }
            };

    @SuppressWarnings("serial")
    private static final Map<String, Object> RUNTIME_ATTRIBUTES = new HashMap<String, Object>()
    {
        {
            put("attr_1", "val_1");
            put("attr_2", "val_2");
        }
    };

    @Mock private Bundle bundle;

    @Mock private DTO testDTO;

    @Mock private ExtServletContext context_0;
    @Mock private ExtServletContext context_A;
    @Mock private ExtServletContext context_B;
    @Mock private ExtServletContext context_C;
    @Mock private ExtServletContext context_D;

    @Mock private ServiceReference<?> listener_1;
    @Mock private ServiceReference<?> listener_2;

    @Mock private ServiceReference<Object> resource;

    private RegistryRuntime registry;
    private Map<String, Object> runtimeAttributes;
	private ListenerRegistry listenerRegistry;

    @Before
    public void setUp()
    {
        registry = null;
        runtimeAttributes = RUNTIME_ATTRIBUTES;
        listenerRegistry = new ListenerRegistry(bundle);
    }

    public ServletContextHelperRuntime setupContext(ServletContext context, String name, long serviceId)
    {
        when(context.getServletContextName()).thenReturn(name);

        String path = "/" + name;
        when(context.getContextPath()).thenReturn(path);

        List<String> initParameterNames = asList("param_1", "param_2");
        when(context.getInitParameterNames()).thenReturn(Collections.enumeration(initParameterNames));
        when(context.getInitParameter("param_1")).thenReturn("init_val_1");
        when(context.getInitParameter("param_2")).thenReturn("init_val_2");

        Map<String, String> initParameters = createInitParameterMap();
        ServletContextHelperInfo contextInfo = createContextInfo(0, serviceId, name, path, initParameters);

        PerContextEventListener eventListener = listenerRegistry.addContext(contextInfo);
        ServletContextHelperRuntime contextHandler = new ContextHandler(contextInfo, context, eventListener, bundle);

        ServletContext sharedContext = contextHandler.getSharedContext();
        sharedContext.setAttribute("intAttr", 1);
        sharedContext.setAttribute("dateAttr", new Date());
        sharedContext.setAttribute("stringAttr", "one");
        sharedContext.setAttribute("dtoAttr", testDTO);

        return contextHandler;
    }

    private Map<String, String> createInitParameterMap()
    {
        Map<String, String> initParameters = new HashMap<String, String>();
        initParameters.put("param_1", "init_val_1");
        initParameters.put("param_2", "init_val_2");
        return initParameters;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Collection<ServiceReference<?>>> setupListeners()
    {
        Map<Long, Collection<ServiceReference<?>>> listenerRuntimes = new HashMap<Long, Collection<ServiceReference<?>>>();

        listenerRuntimes.put(ID_0, asList(listener_1, listener_2));
        listenerRuntimes.put(ID_A, Arrays.<ServiceReference<?>>asList(listener_1));
        listenerRuntimes.put(ID_B, asList(listener_1, listener_2));

        when(listener_1.getProperty(Constants.SERVICE_ID)).thenReturn(ID_LISTENER_1);
        when(listener_1.getProperty(Constants.OBJECTCLASS))
                .thenReturn(new String[] { "org.test.interface_1" });

        when(listener_2.getProperty(Constants.SERVICE_ID)).thenReturn(ID_LISTENER_2);
        when(listener_2.getProperty(Constants.OBJECTCLASS))
                .thenReturn(new String[] { "org.test.interface_1", "org.test.interface_2" });

        return listenerRuntimes;
    }

    public void setupResource()
    {
        when(resource.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PATTERN)).thenReturn(new String[] { "/" });
        when(resource.getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PREFIX)).thenReturn("prefix");
    }

    public void setupRegistry(List<ServletContextHelperRuntime> contexts,
            List<ContextRuntime> contextRuntimes,
            Map<Long, Collection<ServiceReference<?>>> listenerRuntimes,
            FailureRuntime failures)
    {
        registry = new RegistryRuntime(contexts, contextRuntimes, listenerRuntimes, failures);
    }

    @Test
    public void buildRuntimeDTO()
    {
        ServletContextHelperRuntime contextHelper_0 = setupContext(context_0, "0", ID_0);
        ServletContextHelperRuntime contextHelper_A = setupContext(context_A, "A", ID_A);
        ServletContextHelperRuntime contextHelper_B = setupContext(context_B, "B", ID_B);

        List<ServletRuntime> servlets_0 = asList(createTestServlet("1", context_0));
        List<FilterRuntime> filters_0 = asList(createTestFilter("1", context_0));
        List<ServletRuntime> resources_0 = asList(createTestServlet("1", context_0));
        List<ErrorPageRuntime> errorPages_0 = asList(createErrorPage("E_1", context_0));
        ContextRuntime contextRuntime_0 = new ContextRuntime(servlets_0, filters_0, resources_0, errorPages_0, ID_0);

        List<ServletRuntime> servlets_A = asList(createTestServlet("A_1", context_A));
        List<FilterRuntime> filters_A = asList(createTestFilter("A_1", context_A));
        List<ServletRuntime> resources_A = asList(createTestServlet("A_1", context_A));
        List<ErrorPageRuntime> errorPages_A = asList(createErrorPage("E_A_1", context_A));
        ContextRuntime contextRuntime_A = new ContextRuntime(servlets_A, filters_A, resources_A, errorPages_A, ID_A);

        List<ServletRuntime> servlets_B = asList(createTestServletWithServiceId("B_1", context_B),
                createTestServletWithServiceId("B_2", context_B));
        List<FilterRuntime> filters_B = asList(createTestFilterWithServiceId("B_1", context_B),
                createTestFilterWithServiceId("B_2", context_B));
        List<ServletRuntime> resources_B = asList(createTestServletWithServiceId("B_1", context_B),
                createTestServletWithServiceId("B_2", context_B));
        List<ErrorPageRuntime> errorPages_B = asList(createErrorPageWithServiceId("E_B_1", context_B),
                createErrorPageWithServiceId("E_B_2", context_B));
        ContextRuntime contextRuntime_B = new ContextRuntime(servlets_B, filters_B, resources_B, errorPages_B, ID_B);

        Map<Long, Collection<ServiceReference<?>>> listenerRuntimes = setupListeners();

        setupRegistry(asList(contextHelper_0, contextHelper_A, contextHelper_B),
                asList(contextRuntime_0, contextRuntime_A, contextRuntime_B),
                listenerRuntimes,
                FailureRuntime.empty());

        RuntimeDTO runtimeDTO = new RuntimeDTOBuilder(registry, runtimeAttributes).build();

        assertEquals(0, runtimeDTO.failedErrorPageDTOs.length);
        assertEquals(0, runtimeDTO.failedFilterDTOs.length);
        assertEquals(0, runtimeDTO.failedListenerDTOs.length);
        assertEquals(0, runtimeDTO.failedResourceDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(0, runtimeDTO.failedServletDTOs.length);

        assertServletContextDTOs(runtimeDTO);
    }

    private void assertServletContextDTOs(RuntimeDTO runtimeDTO)
    {
        SortedSet<Long> seenServiceIds = new TreeSet<Long>();

        assertEquals(3, runtimeDTO.servletContextDTOs.length);

        for (ServletContextDTO servletContextDTO : runtimeDTO.servletContextDTOs)
        {
            String contextName = servletContextDTO.name;
            assertTrue(CONTEXT_NAMES.contains(contextName));
            if (contextName.equals("0"))
            {
                assertTrue(contextName,
                        servletContextDTO.serviceId < 0);
                assertEquals(contextName,
                        1, servletContextDTO.servletDTOs.length);
                assertEquals(contextName,
                        1, servletContextDTO.filterDTOs.length);
                assertEquals(contextName,
                        1, servletContextDTO.resourceDTOs.length);
                assertEquals(contextName,
                        1, servletContextDTO.errorPageDTOs.length);
                assertEquals(contextName,
                        2, servletContextDTO.listenerDTOs.length);
            }
            else
            {
                int expectedId = CONTEXT_NAMES.indexOf(contextName) + 1;
                assertEquals(contextName,
                        expectedId, servletContextDTO.serviceId);

                int expectedChildren = CONTEXT_NAMES.indexOf(contextName);
                assertEquals(contextName,
                        expectedChildren, servletContextDTO.servletDTOs.length);
                assertEquals(contextName,
                        expectedChildren, servletContextDTO.filterDTOs.length);
                assertEquals(contextName,
                        expectedChildren, servletContextDTO.resourceDTOs.length);
                assertEquals(contextName,
                        expectedChildren, servletContextDTO.errorPageDTOs.length);
                assertEquals(contextName,
                        expectedChildren, servletContextDTO.listenerDTOs.length);
            }
            seenServiceIds.add(servletContextDTO.serviceId);

            assertEquals(contextName,
                    3, servletContextDTO.attributes.size());
            assertEquals(contextName,
                    1, servletContextDTO.attributes.get("intAttr"));
            assertEquals(contextName,
                    "one", servletContextDTO.attributes.get("stringAttr"));
            assertEquals(contextName,
                    testDTO, servletContextDTO.attributes.get("dtoAttr"));

            assertEquals(contextName,
                    2, servletContextDTO.initParams.size());
            assertEquals(contextName,
                    "init_val_1", servletContextDTO.initParams.get("param_1"));
            assertEquals(contextName,
                    "init_val_2", servletContextDTO.initParams.get("param_2"));

            assertEquals(contextName,
                    "/" + contextName + "/" + contextName, servletContextDTO.contextPath);

            Collection<Long> serviceIds = assertServletDTOs(contextName,
                    servletContextDTO.serviceId, servletContextDTO.servletDTOs);
            seenServiceIds.addAll(serviceIds);

            serviceIds = assertFilterDTOs(contextName,
                    servletContextDTO.serviceId, servletContextDTO.filterDTOs);
            seenServiceIds.addAll(serviceIds);

            serviceIds = assertResourceDTOs(contextName,
                    servletContextDTO.serviceId, servletContextDTO.resourceDTOs);
            seenServiceIds.addAll(serviceIds);

            serviceIds = assertErrorPageDTOs(contextName,
                    servletContextDTO.serviceId, servletContextDTO.errorPageDTOs);
            seenServiceIds.addAll(serviceIds);

            serviceIds = assertListenerDTOs(contextName,
                    servletContextDTO.serviceId, servletContextDTO.listenerDTOs);
            seenServiceIds.addAll(serviceIds);
        }
        assertEquals(12, seenServiceIds.tailSet(0L).size());
        assertEquals(9, seenServiceIds.headSet(0L).size());
    }

    private Collection<Long> assertServletDTOs(String contextName, long contextId, ServletDTO[] dtos) {
        List<Long> serviceIds = new ArrayList<Long>();
        for (ServletDTO servletDTO : dtos)
        {
            String name = servletDTO.name;
            assertTrue(CONTEXT_ENTITY_NAMES.get(contextName).contains(name));

            if (contextId != ID_B)
            {
                assertTrue(name,
                        servletDTO.serviceId < 0);
            }
            else
            {
                assertTrue(name,
                        servletDTO.serviceId > 0);
            }
            serviceIds.add(servletDTO.serviceId);

            assertEquals(name,
                    contextId, servletDTO.servletContextId);

            assertTrue(name,
                    servletDTO.asyncSupported);

            assertEquals(name,
                    2, servletDTO.initParams.size());
            assertEquals(name,
                    "valOne_" + name, servletDTO.initParams.get("paramOne_" + name));
            assertEquals(name,
                    "valTwo_" + name, servletDTO.initParams.get("paramTwo_" + name));

            assertEquals(name,
                    1, servletDTO.patterns.length);
            assertEquals(name,
                    "/" + name, servletDTO.patterns[0]);

            assertEquals(name,
                    "info_" + name, servletDTO.servletInfo);
        }

        return serviceIds;
    }

    private Collection<Long> assertFilterDTOs(String contextName, long contextId, FilterDTO[] dtos) {
        List<Long> serviceIds = new ArrayList<Long>();
        for (FilterDTO filterDTO : dtos)
        {
            String name = filterDTO.name;
            assertTrue(CONTEXT_ENTITY_NAMES.get(contextName).contains(name));

            if (contextId != ID_B)
            {
                assertTrue(name,
                        filterDTO.serviceId < 0);
            }
            else
            {
                assertTrue(name,
                        filterDTO.serviceId > 0);
            }
            serviceIds.add(filterDTO.serviceId);

            assertEquals(name,
                    contextId, filterDTO.servletContextId);

            assertTrue(name,
                    filterDTO.asyncSupported);

            assertEquals(name,
                    2, filterDTO.initParams.size());
            assertEquals(name,
                    "valOne_" + name, filterDTO.initParams.get("paramOne_" + name));
            assertEquals(name,
                    "valTwo_" + name, filterDTO.initParams.get("paramTwo_" + name));

            assertEquals(name,
                    1, filterDTO.patterns.length);
            assertEquals(name,
                    "/" + name, filterDTO.patterns[0]);

            assertEquals(name,
                    1, filterDTO.regexs.length);
            assertEquals(name,
                    "." + name, filterDTO.regexs[0]);

            assertEquals(name,
                    2, filterDTO.dispatcher.length);
            assertEquals(name,
                    "ASYNC", filterDTO.dispatcher[0]);
            assertEquals(name,
                    "REQUEST", filterDTO.dispatcher[1]);
        }

        return serviceIds;
    }

    private Collection<Long> assertResourceDTOs(String contextName, long contextId, ResourceDTO[] dtos) {
        List<Long> serviceIds = new ArrayList<Long>();
        for (ResourceDTO resourceDTO : dtos)
        {
            if (contextId != ID_B)
            {
                assertTrue(contextId + " " + contextName,
                        resourceDTO.serviceId < 0);
            }
            else
            {
                assertTrue(contextId + " " + contextName,
                        resourceDTO.serviceId > 0);
            }
            serviceIds.add(resourceDTO.serviceId);

            assertEquals(contextId + " " + contextName,
                    contextId, resourceDTO.servletContextId);

            assertEquals(contextId + " " + contextName,
                    1, resourceDTO.patterns.length);
            assertTrue(contextId + " " + contextName,
                    resourceDTO.patterns[0].startsWith("/"));
        }
        return serviceIds;
    }

    private Collection<Long> assertErrorPageDTOs(String contextName, long contextId, ErrorPageDTO[] dtos)
    {
        List<Long> serviceIds = new ArrayList<Long>();
        for (ErrorPageDTO  errorPageDTO : dtos)
        {
            String name = errorPageDTO.name;
            assertTrue(CONTEXT_ENTITY_NAMES.get(contextName).contains(name.substring(2)));

            if (contextId != ID_B)
            {
                assertTrue(name,
                        errorPageDTO.serviceId < 0);
            }
            else
            {
                assertTrue(name,
                        errorPageDTO.serviceId > 0);
            }
            serviceIds.add(errorPageDTO.serviceId);

            assertEquals(name,
                    contextId, errorPageDTO.servletContextId);

            assertTrue(name,
                    errorPageDTO.asyncSupported);

            assertEquals(name,
                    2, errorPageDTO.initParams.size());
            assertEquals(name,
                    "valOne_" + name, errorPageDTO.initParams.get("paramOne_" + name));
            assertEquals(name,
                    "valTwo_" + name, errorPageDTO.initParams.get("paramTwo_" + name));

            assertEquals(name,
                    "info_" + name, errorPageDTO.servletInfo);

            assertEquals(name,
                    2, errorPageDTO.errorCodes.length);
            long[] errorCodes = copyOf(errorPageDTO.errorCodes, 2);
            sort(errorCodes);
            assertEquals(name,
                    400, errorCodes[0]);
            assertEquals(name,
                    500, errorCodes[1]);

            assertEquals(name,
                    2, errorPageDTO.exceptions.length);
            String[] exceptions = copyOf(errorPageDTO.exceptions, 2);
            sort(exceptions);
            assertEquals(name,
                    "Bad request", exceptions[0]);
            assertEquals(name,
                    "Error", exceptions[1]);
        }

        return serviceIds;
    }

    private Collection<Long> assertListenerDTOs(String contextName, long contextId, ListenerDTO[] dtos)
    {
        Set<Long> serviceIds = new HashSet<Long>();
        for (ListenerDTO listenerDTO : dtos)
        {
            assertEquals(contextId, listenerDTO.servletContextId);
            serviceIds.add(listenerDTO.serviceId);
        }

        assertEquals(ID_LISTENER_1.longValue(), dtos[0].serviceId);
        assertArrayEquals(new String[] { "org.test.interface_1" },
                dtos[0].types);
        if (dtos.length > 1)
        {
            assertEquals(ID_LISTENER_2.longValue(), dtos[1].serviceId);
            assertArrayEquals(new String[] { "org.test.interface_1", "org.test.interface_2" }, dtos[1].types);
        }

        return serviceIds;
    }

    @Test
    public void nullValuesInEntities() {
        ServletContextHelperRuntime contextHandler = setupContext(context_0, "0", ID_0);

        ServletInfo servletInfo = createServletInfo(0,
                ID_COUNTER.incrementAndGet(),
                "1",
                new String[] { "/*" },
                null,
                true,
                Collections.<String, String>emptyMap());
        Servlet servlet = mock(Servlet.class);
        ServletRuntime servletHandler = new ServletHandler(null, context_0, servletInfo, servlet);
        when(servlet.getServletInfo()).thenReturn("info_0");

        FilterInfo filterInfo = createFilterInfo(0,
                ID_COUNTER.incrementAndGet(),
                "1",
                null,
                null,
                null,
                true,
                null,
                Collections.<String, String>emptyMap());
        FilterRuntime filterHandler = new FilterHandler(null, context_0, mock(Filter.class), filterInfo);

        ServletInfo resourceInfo = createServletInfo(0,
                ID_COUNTER.incrementAndGet(),
                "1",
                new String[] { "/*" },
                null,
                true,
                Collections.<String, String>emptyMap());
        Servlet resource = mock(Servlet.class);
        ServletRuntime resourceHandler = new ServletHandler(null, context_0, resourceInfo, resource);

        ContextRuntime contextRuntime = new ContextRuntime(asList(servletHandler),
                asList(filterHandler),
                asList(resourceHandler),
                Collections.<ErrorPageRuntime>emptyList(),
                ID_0);
        setupRegistry(asList(contextHandler), asList(contextRuntime),
                Collections.<Long, Collection<ServiceReference<?>>>emptyMap(),
                FailureRuntime.empty());

        RuntimeDTO runtimeDTO = new RuntimeDTOBuilder(registry, runtimeAttributes).build();

        assertEquals(1, runtimeDTO.servletContextDTOs.length);
        assertEquals(1, runtimeDTO.servletContextDTOs[0].servletDTOs.length);
        assertEquals(1, runtimeDTO.servletContextDTOs[0].filterDTOs.length);

        assertEquals(emptyMap(), runtimeDTO.servletContextDTOs[0].servletDTOs[0].initParams);

        assertEquals(emptyMap(), runtimeDTO.servletContextDTOs[0].filterDTOs[0].initParams);
        assertEquals(0, runtimeDTO.servletContextDTOs[0].filterDTOs[0].patterns.length);
        assertEquals(0, runtimeDTO.servletContextDTOs[0].filterDTOs[0].regexs.length);
    }

    @Test
    public void contextWithNoEntities() {
        ServletContextHelperRuntime contextHandler_0 = setupContext(context_0, "0", ID_0);
        ServletContextHelperRuntime contextHandler_A = setupContext(context_A, "A", ID_A);

        setupRegistry(asList(contextHandler_0, contextHandler_A),
                asList(ContextRuntime.empty(ID_0), ContextRuntime.empty(ID_A)),
                Collections.<Long, Collection<ServiceReference<?>>>emptyMap(),
                FailureRuntime.empty());

        RuntimeDTO runtimeDTO = new RuntimeDTOBuilder(registry, runtimeAttributes).build();

        assertEquals(2, runtimeDTO.servletContextDTOs.length);
        assertEquals(0, runtimeDTO.servletContextDTOs[0].servletDTOs.length);
        assertEquals(0, runtimeDTO.servletContextDTOs[0].filterDTOs.length);
        assertEquals(0, runtimeDTO.servletContextDTOs[1].servletDTOs.length);
        assertEquals(0, runtimeDTO.servletContextDTOs[1].filterDTOs.length);
    }

    @Test
    public void missingPatternInServletThrowsException()
    {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("patterns");

        ServletContextHelperRuntime contextHandler = setupContext(context_0, "0", ID_0);

        ServletInfo servletInfo = createServletInfo(0,
                ID_COUNTER.incrementAndGet(),
                "1",
                null,
                null,
                true,
                Collections.<String, String>emptyMap());
        Servlet servlet = mock(Servlet.class);
        ServletRuntime servletHandler = new ServletHandler(null, context_0, servletInfo, servlet);
        when(servlet.getServletInfo()).thenReturn("info_0");

        ContextRuntime contextRuntime = new ContextRuntime(asList(servletHandler),
                Collections.<FilterRuntime>emptyList(),
                Collections.<ServletRuntime>emptyList(),
                Collections.<ErrorPageRuntime>emptyList(),
                ID_0);
        setupRegistry(asList(contextHandler), asList(contextRuntime),
                Collections.<Long, Collection<ServiceReference<?>>>emptyMap(),
                FailureRuntime.empty());

        new RuntimeDTOBuilder(registry, runtimeAttributes).build();
    }

    public FailureRuntime setupFailures()
    {
        Map<AbstractInfo<?>, Integer> serviceFailures = new HashMap<AbstractInfo<?>, Integer>();

        ServletContextHelperInfo contextInfo = createContextInfo(0,
                ID_C,
                "context_failure_1",
                "/",
                createInitParameterMap());
        serviceFailures.put(contextInfo, 1);

        contextInfo = createContextInfo(0,
                ID_D,
                "context_failure_2",
                "/",
                createInitParameterMap());
        serviceFailures.put(contextInfo, 2);

        ServletInfo servletInfo = createServletInfo(0, ID_COUNTER.incrementAndGet(),
                "servlet_failure_1",
                new String[] {"/"},
                null,
                false,
                createInitParameterMap());
        serviceFailures.put(servletInfo, 3);

        servletInfo = createServletInfo(0, ID_COUNTER.incrementAndGet(),
                "servlet_failure_2",
                new String[] {"/"},
                null,
                false,
                createInitParameterMap());
        serviceFailures.put(servletInfo, 4);

        FilterInfo filterInfo = createFilterInfo(0,
                ID_COUNTER.incrementAndGet(),
                "filter_failure_1",
                new String[] {"/"},
                null,
                null,
                false,
                null,
                createInitParameterMap());
        serviceFailures.put(filterInfo, 5);

        ServletInfo errorPageInfo = createServletInfo(0,
                ID_COUNTER.incrementAndGet(),
                "error_failure_1",
                null,
                new String[] { "405", "TestException" },
                false,
                createInitParameterMap());
        serviceFailures.put(errorPageInfo, 6);

        ServletInfo invalidErrorPageInfo = createServletInfo(0,
                ID_COUNTER.incrementAndGet(),
                "error_failure_2",
                new String[] { "/" },
                new String[] { "405", "TestException" },
                false,
                createInitParameterMap());
        serviceFailures.put(invalidErrorPageInfo, 7);

        return FailureRuntime.builder().add(serviceFailures).build();
    }

    @Test
    public void testFailureDTOs()
    {
        setupRegistry(Collections.<ServletContextHelperRuntime>emptyList(),
                Collections.<ContextRuntime>emptyList(),
                Collections.<Long, Collection<ServiceReference<?>>>emptyMap(),
                setupFailures());

        RuntimeDTO runtimeDTO = new RuntimeDTOBuilder(registry, runtimeAttributes).build();

        assertEquals(0, runtimeDTO.servletContextDTOs.length);

        assertEquals(2, runtimeDTO.failedErrorPageDTOs.length);
        assertEquals(1, runtimeDTO.failedFilterDTOs.length);
        // ListenerInfo is hard to setup
        assertEquals(0, runtimeDTO.failedListenerDTOs.length);
        // ResourceInfo is hard to setup
        assertEquals(0, runtimeDTO.failedResourceDTOs.length);
        assertEquals(2, runtimeDTO.failedServletContextDTOs.length);
        assertEquals(2, runtimeDTO.failedServletDTOs.length);

        assertEquals(1, runtimeDTO.failedServletContextDTOs[0].failureReason);
        assertEquals(2, runtimeDTO.failedServletContextDTOs[1].failureReason);
        assertEquals(3, runtimeDTO.failedServletDTOs[0].failureReason);
        assertEquals(4, runtimeDTO.failedServletDTOs[1].failureReason);
        assertEquals(5, runtimeDTO.failedFilterDTOs[0].failureReason);
        assertEquals(6, runtimeDTO.failedErrorPageDTOs[0].failureReason);
        assertEquals(7, runtimeDTO.failedErrorPageDTOs[1].failureReason);

        assertEquals("context_failure_1", runtimeDTO.failedServletContextDTOs[0].name);
        assertEquals("context_failure_2", runtimeDTO.failedServletContextDTOs[1].name);
        assertEquals("servlet_failure_1", runtimeDTO.failedServletDTOs[0].name);
        assertEquals("servlet_failure_2", runtimeDTO.failedServletDTOs[1].name);
        assertEquals("filter_failure_1", runtimeDTO.failedFilterDTOs[0].name);
        assertEquals("error_failure_1", runtimeDTO.failedErrorPageDTOs[0].name);
        assertEquals("error_failure_2", runtimeDTO.failedErrorPageDTOs[1].name);

        assertEquals(ID_C.longValue(), runtimeDTO.failedServletContextDTOs[0].serviceId);
        assertEquals(ID_D.longValue(), runtimeDTO.failedServletContextDTOs[1].serviceId);
        assertEquals(0, runtimeDTO.failedServletDTOs[0].servletContextId);
        assertEquals(0, runtimeDTO.failedServletDTOs[1].servletContextId);
        assertEquals(0, runtimeDTO.failedFilterDTOs[0].servletContextId);
        assertEquals(0, runtimeDTO.failedErrorPageDTOs[0].servletContextId);
        assertEquals(0, runtimeDTO.failedErrorPageDTOs[1].servletContextId);

        assertEquals(0, runtimeDTO.failedServletContextDTOs[0].errorPageDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[0].filterDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[0].listenerDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[0].resourceDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[0].servletDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[1].errorPageDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[1].filterDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[1].listenerDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[1].resourceDTOs.length);
        assertEquals(0, runtimeDTO.failedServletContextDTOs[1].servletDTOs.length);

        assertTrue(runtimeDTO.failedServletContextDTOs[0].attributes.isEmpty());
        assertTrue(runtimeDTO.failedServletContextDTOs[1].attributes.isEmpty());
    }
}