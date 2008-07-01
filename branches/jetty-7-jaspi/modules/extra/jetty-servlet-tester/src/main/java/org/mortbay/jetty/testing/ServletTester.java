package org.mortbay.jetty.testing;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;

import org.mortbay.io.ByteArrayBuffer;
import org.mortbay.jetty.LocalConnector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.util.Attributes;



/* ------------------------------------------------------------ */
/** Testing support for servlets and filters.
 * 
 * Allows a programatic setup of a context with servlets and filters for 
 * testing.  Raw HTTP requests may be sent to the context and responses received.
 * To avoid handling raw HTTP see {@link org.mortbay.jetty.testing.HttpTester}.
 * <pre>
 *      ServletTester tester=new ServletTester();
 *      tester.setContextPath("/context");
 *      tester.addServlet(TestServlet.class, "/servlet/*");
 *      tester.addServlet("org.mortbay.jetty.servlet.DefaultServlet", "/");
 *      tester.start();
 *      String response = tester.getResponses("GET /context/servlet/info HTTP/1.0\r\n\r\n");
 * </pre>
 * 
 * @see org.mortbay.jetty.testing.HttpTester
 * @author gregw
 *
 */
public class ServletTester
{
    Server _server = new Server();
    LocalConnector _connector = new LocalConnector();
//    Context _context = new Context(Context.SESSIONS|Context.SECURITY);
    //jaspi why security if it is not set up?
    Context _context = new Context(Context.SESSIONS);

    public ServletTester()
    {
        try
        {
            _server.setSendServerVersion(false);
            _server.addConnector(_connector);
            _server.addHandler(_context);
        }
        catch (Error e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /* ------------------------------------------------------------ */
    public void start() throws Exception
    {
        _server.start();
    }
    
    /* ------------------------------------------------------------ */
    public void stop() throws Exception
    {
        _server.stop();
    }
    
    /* ------------------------------------------------------------ */
    /** Get raw HTTP responses from raw HTTP requests.
     * Multiple requests and responses may be handled, but only if
     * persistent connections conditions apply.
     * @param rawRequests String of raw HTTP requests
     * @return String of raw HTTP responses
     * @throws Exception
     */
    public String getResponses(String rawRequests) throws Exception
    {
        _connector.reopen();
        //System.err.println(">>>>\n"+rawRequests);
        String responses = _connector.getResponses(rawRequests);
        //System.err.println("<<<<\n"+responses);
        return responses;
    }

    /* ------------------------------------------------------------ */
    /** Get raw HTTP responses from raw HTTP requests.
     * Multiple requests and responses may be handled, but only if
     * persistent connections conditions apply.
     * @param rawRequests String of raw HTTP requests
     * @return String of raw HTTP responses
     * @throws Exception
     */
    public ByteArrayBuffer getResponses(ByteArrayBuffer rawRequests) throws Exception
    {
        _connector.reopen();
        ByteArrayBuffer responses = _connector.getResponses(rawRequests,false);
        return responses;
    }
    
    /* ------------------------------------------------------------ */
    /** Create a Socket connector.
     * This methods adds a socket connector to the server
     * @param locahost if true, only listen on local host, else listen on all interfaces.
     * @return A URL to access the server via the socket connector.
     * @throws Exception
     */
    public String createSocketConnector(boolean localhost)
    throws Exception
    {
       SocketConnector connector = new SocketConnector();
       if (localhost)
           connector.setHost("127.0.0.1");
       _server.addConnector(connector);
       if (_server.isStarted())
           connector.start();
       else
           connector.open();
       
       return "http://"+(localhost?"127.0.0.1":
       InetAddress.getLocalHost().getHostAddress()    
       )+":"+connector.getLocalPort();
   }

    /* ------------------------------------------------------------ */
    /**
     * @param listener
     * @see org.mortbay.jetty.handler.ContextHandler#addEventListener(java.util.EventListener)
     */
    public void addEventListener(EventListener listener)
    {
        _context.addEventListener(listener);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param filterClass
     * @param pathSpec
     * @param dispatches
     * @return
     * @see org.mortbay.jetty.servlet.Context#addFilter(java.lang.Class, java.lang.String, int)
     */
    public FilterHolder addFilter(Class filterClass, String pathSpec, int dispatches)
    {
        return _context.addFilter(filterClass,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param filterClass
     * @param pathSpec
     * @param dispatches
     * @return
     * @see org.mortbay.jetty.servlet.Context#addFilter(java.lang.String, java.lang.String, int)
     */
    public FilterHolder addFilter(String filterClass, String pathSpec, int dispatches)
    {
        return _context.addFilter(filterClass,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param servlet
     * @param pathSpec
     * @return
     * @see org.mortbay.jetty.servlet.Context#addServlet(java.lang.Class, java.lang.String)
     */
    public ServletHolder addServlet(Class servlet, String pathSpec)
    {
        return _context.addServlet(servlet,pathSpec);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param className
     * @param pathSpec
     * @return
     * @see org.mortbay.jetty.servlet.Context#addServlet(java.lang.String, java.lang.String)
     */
    public ServletHolder addServlet(String className, String pathSpec)
    {
        return _context.addServlet(className,pathSpec);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param name
     * @return
     * @see org.mortbay.jetty.handler.ContextHandler#getAttribute(java.lang.String)
     */
    public Object getAttribute(String name)
    {
        return _context.getAttribute(name);
    }

    /* ------------------------------------------------------------ */
    /**
     * @return
     * @see org.mortbay.jetty.handler.ContextHandler#getAttributeNames()
     */
    public Enumeration getAttributeNames()
    {
        return _context.getAttributeNames();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return
     * @see org.mortbay.jetty.handler.ContextHandler#getAttributes()
     */
    public Attributes getAttributes()
    {
        return _context.getAttributes();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return
     * @see org.mortbay.jetty.handler.ContextHandler#getResourceBase()
     */
    public String getResourceBase()
    {
        return _context.getResourceBase();
    }

    /* ------------------------------------------------------------ */
    /**
     * @param name
     * @param value
     * @see org.mortbay.jetty.handler.ContextHandler#setAttribute(java.lang.String, java.lang.Object)
     */
    public void setAttribute(String name, Object value)
    {
        _context.setAttribute(name,value);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param classLoader
     * @see org.mortbay.jetty.handler.ContextHandler#setClassLoader(java.lang.ClassLoader)
     */
    public void setClassLoader(ClassLoader classLoader)
    {
        _context.setClassLoader(classLoader);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param contextPath
     * @see org.mortbay.jetty.handler.ContextHandler#setContextPath(java.lang.String)
     */
    public void setContextPath(String contextPath)
    {
        _context.setContextPath(contextPath);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param eventListeners
     * @see org.mortbay.jetty.handler.ContextHandler#setEventListeners(java.util.EventListener[])
     */
    public void setEventListeners(EventListener[] eventListeners)
    {
        _context.setEventListeners(eventListeners);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param resourceBase
     * @see org.mortbay.jetty.handler.ContextHandler#setResourceBase(java.lang.String)
     */
    public void setResourceBase(String resourceBase)
    {
        _context.setResourceBase(resourceBase);
    }
    
}
