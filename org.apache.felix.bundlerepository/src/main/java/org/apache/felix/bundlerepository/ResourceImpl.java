/*
 *   Copyright 2006 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.felix.bundlerepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import org.osgi.framework.Version;
import org.osgi.service.obr.*;

public class ResourceImpl implements Resource, Map
{
    private final String URI = "uri";

    private Repository m_repo = null;
    private Map m_map = null;
    private List m_catList = new ArrayList();
    private List m_capList = new ArrayList();
    private List m_reqList = new ArrayList();

    private String m_resourceURI = "";
    private String m_docURI = "";
    private String m_licenseURI = "";
    private String m_sourceURI = "";
    private boolean m_converted = false;

    public ResourceImpl()
    {
        this(null);
    }

    public ResourceImpl(ResourceImpl resource)
    {
        m_map = new TreeMap(new Comparator() {
            public int compare(Object o1, Object o2)
            {
                return o1.toString().compareToIgnoreCase(o2.toString());
            }
        });

        if (resource != null)
        {
            resource.putAll(resource.getProperties());
            m_catList.addAll(resource.m_catList);
            m_capList.addAll(resource.m_capList);
            m_reqList.addAll(resource.m_reqList);
        }
    }

    public boolean equals(Object o)
    {
        if (o instanceof Resource)
        {
            return ((Resource) o).getSymbolicName().equals(getSymbolicName())
                && ((Resource) o).getVersion().equals(getVersion());
        }
        return false;
    }

    public int hashCode()
    {
        return getSymbolicName().hashCode() ^ getVersion().hashCode();
    }

    public Map getProperties()
    {
        if (!m_converted)
        {
            convertURItoURL();
        }
        return m_map;
    }

    public String getPresentationName()
    {
        return (String) m_map.get(PRESENTATION_NAME);
    }

    public String getSymbolicName()
    {
        return (String) m_map.get(SYMBOLIC_NAME);
    }

    public String getId()
    {
        return (String) m_map.get(ID);
    }

    public Version getVersion()
    {
        return (Version) m_map.get(VERSION);
    }

    public URL getURL()
    {
        if (!m_converted)
        {
            convertURItoURL();
        }
        return (URL) m_map.get(URL);
    }

    public Requirement[] getRequirements()
    {
        return (Requirement[]) m_reqList.toArray(new Requirement[m_reqList.size()]);
    }

    public void addRequire(Requirement req)
    {
        m_reqList.add(req);
    }

    public Capability[] getCapabilities()
    {
        return (Capability[]) m_capList.toArray(new Capability[m_capList.size()]);
    }

    // TODO: OBR - Should this be a property?
    public void addCapability(Capability cap)
    {
        m_capList.add(cap);
    }

    public String[] getCategories()
    {
        return (String[]) m_catList.toArray(new String[m_catList.size()]);
    }

    public void addCategory(CategoryImpl cat)
    {
        m_catList.add(cat.getId());
    }

    public Repository getRepository()
    {
        return m_repo;
    }

    protected void setRepository(Repository repo)
    {
        m_repo = repo;
    }

    //
    // Map interface methods.
    //

    public int size()
    {
        return m_map.size();
    }

    public void clear()
    {
        m_map.clear();
    }

    public boolean isEmpty()
    {
        return m_map.isEmpty();
    }

    public boolean containsKey(Object key)
    {
        return m_map.containsKey(key);
    }

    public boolean containsValue(Object value)
    {
        return m_map.containsValue(value);
    }

    public Collection values()
    {
        return m_map.values();
    }

    public void putAll(Map t)
    {
        m_map.putAll(t);
    }

    public Set entrySet()
    {
        return m_map.entrySet();
    }

    public Set keySet()
    {
        return m_map.keySet();
    }

    public Object get(Object key)
    {
        return m_map.get(key);
    }

    public Object remove(Object key)
    {
        return m_map.remove(key);
    }

    public Object put(Object key, Object value)
    {
        // Capture the URIs since they might be relative, so we
        // need to defer setting the actual URL value until they
        // are used so that we will know our repository and its
        // base URL.
        if (key.equals(LICENSE_URL))
        {
            m_licenseURI = (String) value;
        }
        else if (key.equals(DOCUMENTATION_URL))
        {
            m_docURI = (String) value;
        }
        else if (key.equals(SOURCE_URL))
        {
            m_sourceURI = (String) value;
        }
        else if (key.equals(URI))
        {
            m_resourceURI = (String) value;
        }
        else
        {
            if (key.equals(VERSION))
            {
                value = new Version(value.toString());
            }
            else if (key.equals(SIZE))
            {
                value = Long.valueOf(value.toString());
            }
            // TODO: OBR - These should be handled by the "add" methods above.
            else if (key.equals("require"))
            {
                m_reqList.add(value);
                return null;
            }
            else if (key.equals("capability"))
            {
                m_capList.add(value);
                return null;
            }
            else if (key.equals("category"))
            {
                m_catList.add(value);
                return null;
            }
    
            return m_map.put(key, value);
        }

        return null;
    }

    private void convertURItoURL()
    {
        if (m_repo != null)
        {
            try
            {
                URL base = m_repo.getURL();
                if (m_resourceURI != null)
                {
                    m_map.put(URL, new URL(base, m_resourceURI));
                }
                if (m_docURI != null)
                {
                    m_map.put(DOCUMENTATION_URL, new URL(base, m_docURI));
                }
                if (m_licenseURI != null)
                {
                    m_map.put(LICENSE_URL, new URL(base, m_licenseURI));
                }
                if (m_sourceURI != null)
                {
                    m_map.put(SOURCE_URL, new URL(base, m_sourceURI));
                }
                m_converted = true;
            }
            catch (MalformedURLException ex)
            {
                ex.printStackTrace(System.err);
            }
        }
    }
}