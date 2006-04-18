//========================================================================
//Copyright 2005 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.management;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.loading.PrivateMLet;

import org.mortbay.component.Container;
import org.mortbay.component.Container.Event;
import org.mortbay.log.Log;
import org.mortbay.util.TypeUtil;

public class MBeanContainer implements Container.Listener
{
    private final MBeanServer _server;
    private volatile int _managementPort;
    private final WeakHashMap _beans = new WeakHashMap();
    private final HashMap _unique = new HashMap();

    public synchronized ObjectName findMBean(Object object)
    {
        return (ObjectName) _beans.get(object);
    }

    public synchronized Object findBean(ObjectName oname)
    {
        for (Iterator iter = _beans.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            if (entry.getValue().equals(oname))
                return entry.getKey();
        }
        return null;
    }

    public MBeanContainer(MBeanServer server, Container container)
    {
        this._server = server;
        container.addEventListener(this);
    }

    public void setManagementPort(int port)
    {
        this._managementPort = port;
    }

    public void start()
    {
        if (_managementPort > 0)
        {
            try
            {
                Log.warn("HttpAdaptor for mx4j is not secure");

                PrivateMLet mlet = new PrivateMLet(new URL[0], Thread.currentThread().getContextClassLoader(), false);
                ObjectName mletName = ObjectName.getInstance("mx4j", "name", "HttpAdaptorLoader");
                _server.registerMBean(mlet, mletName);

                ObjectName adaptorName = ObjectName.getInstance("mx4j", "name", "HttpAdaptor");
                _server.createMBean("mx4j.tools.adaptor.http.HttpAdaptor", adaptorName, mletName);
                _server.setAttribute(adaptorName, new Attribute("Port", new Integer(_managementPort)));
                _server.setAttribute(adaptorName, new Attribute("Host", "localhost"));

                ObjectName processorName = ObjectName.getInstance("mx4j", "name", "XSLTProcessor");
                _server.createMBean("mx4j.tools.adaptor.http.XSLTProcessor", processorName, mletName);
                _server.setAttribute(adaptorName, new Attribute("ProcessorName", processorName));

                _server.invoke(adaptorName, "start", null, null);

                Runtime.getRuntime().addShutdownHook(new ShutdownHook(mletName, adaptorName, processorName));
            }
            catch (Exception e)
            {
                Log.warn(e);
            }
        }
    }

    public void add(Event event)
    {
        addBean(event.getParent());
        addBean(event.getChild());
    }

    public void remove(Event event)
    {
        ObjectName oname = findMBean(event.getChild());
        System.err.println("remove "+event.getChild());
        new Throwable().printStackTrace();
        if (oname != null)
        {
            try
            {
                _server.unregisterMBean(oname);
                Log.debug("Unregistered {}", oname);
            }
            catch (javax.management.InstanceNotFoundException e)
            {
                Log.ignore(e);
            }
            catch (Exception e)
            {
                Log.warn(e);
            }
        }
    }

    private synchronized void addBean(Object bean)
    {
        System.err.println("add "+bean);
        try
        {
            if (bean == null || _beans.containsKey(bean))
                return;
            Object mbean = ObjectMBean.mbeanFor(bean);
            if (mbean == null)
                return;

            if (mbean instanceof ObjectMBean)
                ((ObjectMBean) mbean).setMBeanContainer(this);
            
            String name = bean.getClass().getName().toLowerCase();
            int dot = name.lastIndexOf('.');
            if (dot >= 0)
                name = name.substring(dot + 1);
            Integer count = (Integer) _unique.get(name);
            count = TypeUtil.newInteger(count == null ? 0 : (1 + count.intValue()));
            _unique.put(name, count);

            ObjectName oname = ObjectName.getInstance("", name, String.valueOf(count));

            
            ObjectInstance oinstance = _server.registerMBean(mbean, oname);
            Log.debug("Registered {}" , oinstance.getObjectName());
            _beans.put(bean, oinstance.getObjectName());

        }
        catch (Exception e)
        {
            Log.warn("bean: "+bean,e);
        }
    }

    private class ShutdownHook extends Thread
    {
        private final ObjectName mletName;
        private final ObjectName adaptorName;
        private final ObjectName processorName;

        public ShutdownHook(ObjectName mletName, ObjectName adaptorName, ObjectName processorName)
        {
            this.mletName = mletName;
            this.adaptorName = adaptorName;
            this.processorName = processorName;
        }

        public void run()
        {
            halt();
            unregister(processorName);
            unregister(adaptorName);
            unregister(mletName);
        }

        private void halt()
        {
            try
            {
                _server.invoke(adaptorName, "stop", null, null);
            }
            catch (Exception e)
            {
                Log.warn(e);
            }
        }

        private void unregister(ObjectName objectName)
        {
            try
            {
                _server.unregisterMBean(objectName);
                Log.debug("Unregistered " + objectName);
            }
            catch (Exception e)
            {
                Log.warn(e);
            }
        }
    }
}
