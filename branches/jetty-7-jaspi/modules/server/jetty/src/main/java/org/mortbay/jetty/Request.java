//========================================================================
//$Id: Request.java,v 1.15 2005/11/16 22:02:40 gregwilkins Exp $
//Copyright 2004-2005 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.mortbay.io.Buffer;
import org.mortbay.io.BufferUtil;
import org.mortbay.io.EndPoint;
import org.mortbay.io.Portable;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.handler.ContextHandler.SContext;
import org.mortbay.jetty.security.UserIdentity;
import org.mortbay.log.Log;
import org.mortbay.util.Attributes;
import org.mortbay.util.AttributesMap;
import org.mortbay.util.LazyList;
import org.mortbay.util.MultiMap;
import org.mortbay.util.StringUtil;
import org.mortbay.util.URIUtil;
import org.mortbay.util.UrlEncoded;
import org.mortbay.util.ajax.Continuation;

/* ------------------------------------------------------------ */
/** Jetty Request.
 * <p>
 * Implements {@link javax.servlet.http.HttpServletRequest} from the {@link javax.servlet.http} package.
 * </p>
 * <p>
 * The standard interface of mostly getters,
 * is extended with setters so that the request is mutable by the handlers that it is
 * passed to.  This allows the request object to be as lightweight as possible and not
 * actually implement any significant behaviour. For example<ul>
 * 
 * <li>The {@link Request#getContextPath} method will return null, until the requeset has been
 * passed to a {@link ContextHandler} which matches the {@link Request#getPathInfo} with a context
 * path and calls {@link Request#setContextPath} as a result.</li>
 * 
 * <li>the HTTP session methods
 * will all return null sessions until such time as a request has been passed to
 * a {@link org.mortbay.jetty.servlet.SessionHandler} which checks for session cookies
 * and enables the ability to create new sessions.</li>
 * 
 * <li>The {@link Request#getServletPath} method will return null until the request has been
 * passed to a {@link org.mortbay.jetty.servlet.ServletHandler} and the pathInfo matched
 * against the servlet URL patterns and {@link Request#setServletPath} called as a result.</li>
 * </ul>
 * 
 * A request instance is created for each {@link HttpConnection} accepted by the server 
 * and recycled for each HTTP request received via that connection. An effort is made
 * to avoid reparsing headers and cookies that are likely to be the same for 
 * requests from the same connection.
 * 
 * @author gregw
 *
 */
public class Request extends Suspendable implements HttpServletRequest
{
    private static final Collection __defaultLocale = Collections.singleton(Locale.getDefault());
    private static final int __NONE=0, _STREAM=1, __READER=2;
    
    private boolean _handled =false;
//    private Map _roleMap;
    private EndPoint _endp;
    
    private Attributes _attributes;
    private String _authType;
    private String _characterEncoding;
    private String _queryEncoding;
    private String _serverName;
    private String _remoteAddr;
    private String _remoteHost;
    private String _method;
    private String _pathInfo;
    private int _port;
    private String _protocol=HttpVersions.HTTP_1_1;
    private String _queryString;
    private String _requestedSessionId;
    private boolean _requestedSessionIdFromCookie=false;
    private String _requestURI;
    private String _scheme=URIUtil.HTTP;
    private String _contextPath;
    private String _servletPath;
    private String _servletName;
    private HttpURI _uri;
//    private Principal _userPrincipal;
    private MultiMap _parameters;
    private MultiMap _baseParameters;
    private boolean _paramsExtracted;
    private int _inputState=__NONE;
    private BufferedReader _reader;
    private String _readerEncoding;
    private boolean _dns=false;
    private ContextHandler.SContext _context;
    private HttpSession _session;
    private SessionManager _sessionManager;
    private boolean _cookiesExtracted=false;
    private long _timeStamp;
    private Buffer _timeStampBuffer;
    private Continuation _continuation;
    private Object _requestAttributeListeners;
    private Map _savedNewSessions;
//    private UserRealm _userRealm;
    private CookieCutter _cookies;
    private UserIdentity _userIdentity = UserIdentity.UNAUTHENTICATED_IDENTITY;

    /* ------------------------------------------------------------ */
    public Request()
    {
        super(null);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * 
     */
    public Request(HttpConnection connection)
    {
        super(connection);
        _endp=connection.getEndPoint();
        _dns=connection.getResolveNames();
    }

    /* ------------------------------------------------------------ */
    protected void recycle()
    {
        super.reset();
        _handled=false;
        if (_context!=null)
            throw new IllegalStateException("Request in context!");
        if(_attributes!=null)
            _attributes.clearAttributes();
        _authType=null;
        _characterEncoding=null;
        _queryEncoding=null;
        _context=null;
        _serverName=null;
        _method=null;
        _pathInfo=null;
        _port=0;
        _protocol=HttpVersions.HTTP_1_1;
        _queryString=null;
        _requestedSessionId=null;
        _requestedSessionIdFromCookie=false;
        _session=null;
        _requestURI=null;
        _scheme=URIUtil.HTTP;
        _servletPath=null;
        _timeStamp=0;
        _timeStampBuffer=null;
        _uri=null;
        _userIdentity=UserIdentity.UNAUTHENTICATED_IDENTITY;
        if (_baseParameters!=null)
            _baseParameters.clear();
        _parameters=null;
        _paramsExtracted=false;
        _inputState=__NONE;
        
        _cookiesExtracted=false;
        if (_savedNewSessions!=null)
            _savedNewSessions.clear();
        _savedNewSessions=null;
        if (_continuation!=null && _continuation.isPending())
            _continuation.reset();
    }

    /* ------------------------------------------------------------ */
    /**
     * Get Request TimeStamp
     * 
     * @return The time that the request was received.
     */
    public Buffer getTimeStampBuffer()
    {
        if (_timeStampBuffer == null && _timeStamp > 0)
                _timeStampBuffer = HttpFields.__dateCache.formatBuffer(_timeStamp);
        return _timeStampBuffer;
    }

    /* ------------------------------------------------------------ */
    /**
     * Get Request TimeStamp
     * 
     * @return The time that the request was received.
     */
    public long getTimeStamp()
    {
        return _timeStamp;
    }

    /* ------------------------------------------------------------ */
    public void setTimeStamp(long ts)
    {
        _timeStamp = ts;
    }

    /* ------------------------------------------------------------ */
    public boolean isHandled()
    {
        return _handled;
    }

    /* ------------------------------------------------------------ */
    public void setHandled(boolean h)
    {
        if (h&& !shouldComplete())
            new Throwable().printStackTrace();
        _handled=h;
    }
    
    
    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getAttribute(java.lang.String)
     */
    public Object getAttribute(String name)
    {
        if ("org.mortbay.jetty.ajax.Continuation".equals(name))
            return getContinuation(true);
            
        if (_attributes==null)
            return null;
        return _attributes.getAttribute(name);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getAttributeNames()
     */
    public Enumeration getAttributeNames()
    {
        if (_attributes==null)
            return Collections.enumeration(Collections.EMPTY_LIST);
        return AttributesMap.getAttributeNamesCopy(_attributes);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getAuthType()
     */
    public String getAuthType()
    {
        return _authType;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getCharacterEncoding()
     */
    public String getCharacterEncoding()
    {
        return _characterEncoding;
    }
    

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getContentLength()
     */
    public int getContentLength()
    {
        return (int)_connection.getRequestFields().getLongField(HttpHeaders.CONTENT_LENGTH_BUFFER);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getContentType()
     */
    public String getContentType()
    {
        return _connection.getRequestFields().getStringField(HttpHeaders.CONTENT_TYPE_BUFFER);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getContentType()
     */
    public void setContentType(String contentType)
    {
        _connection.getRequestFields().put(HttpHeaders.CONTENT_TYPE_BUFFER,contentType);
        
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getContextPath()
     */
    public String getContextPath()
    {
        return _contextPath;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getCookies()
     */
    public Cookie[] getCookies()
    {
        if (_cookiesExtracted) 
            return _cookies==null?null:_cookies.getCookies();

        // Handle no cookies
        if (!_connection.getRequestFields().containsKey(HttpHeaders.COOKIE_BUFFER))
        {
            _cookiesExtracted = true;
            if (_cookies!=null)
                _cookies.reset();
            return null;
        }

        if (_cookies==null)
            _cookies=new CookieCutter();

        Enumeration enm = _connection.getRequestFields().getValues(HttpHeaders.COOKIE_BUFFER);
        while (enm.hasMoreElements())
        {
            String c = (String)enm.nextElement();
            _cookies.addCookieField(c);
        }
        _cookiesExtracted=true;

        return _cookies.getCookies();
    }


    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getDateHeader(java.lang.String)
     */
    public long getDateHeader(String name)
    {
        return _connection.getRequestFields().getDateField(name);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getHeader(java.lang.String)
     */
    public String getHeader(String name)
    {
        return _connection.getRequestFields().getStringField(name);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getHeaderNames()
     */
    public Enumeration getHeaderNames()
    {
        return _connection.getRequestFields().getFieldNames();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getHeaders(java.lang.String)
     */
    public Enumeration getHeaders(String name)
    {
        Enumeration e = _connection.getRequestFields().getValues(name);
        if (e==null)
            return Collections.enumeration(Collections.EMPTY_LIST);
        return e;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getInputStream()
     */
    public ServletInputStream getInputStream() throws IOException
    {
        if (_inputState!=__NONE && _inputState!=_STREAM)
            throw new IllegalStateException("READER");
        _inputState=_STREAM;
        return _connection.getInputStream();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getIntHeader(java.lang.String)
     */
    public int getIntHeader(String name)
    {
        return (int)_connection.getRequestFields().getLongField(name);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getLocalAddr()
     */
    public String getLocalAddr()
    {
        return _endp==null?null:_endp.getLocalAddr();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getLocale()
     */
    public Locale getLocale()
    {
        Enumeration enm = _connection.getRequestFields().getValues(HttpHeaders.ACCEPT_LANGUAGE, HttpFields.__separators);
        
        // handle no locale
        if (enm == null || !enm.hasMoreElements())
            return Locale.getDefault();
        
        // sort the list in quality order
        List acceptLanguage = HttpFields.qualityList(enm);
        if (acceptLanguage.size()==0)
            return  Locale.getDefault();
        
        int size=acceptLanguage.size();
        
        // convert to locals
        for (int i=0; i<size; i++)
        {
            String language = (String)acceptLanguage.get(i);
            language=HttpFields.valueParameters(language,null);
            String country = "";
            int dash = language.indexOf('-');
            if (dash > -1)
            {
                country = language.substring(dash + 1).trim();
                language = language.substring(0,dash).trim();
            }
            return new Locale(language,country);
        }
        
        return  Locale.getDefault();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getLocales()
     */
    public Enumeration getLocales()
    {

        Enumeration enm = _connection.getRequestFields().getValues(HttpHeaders.ACCEPT_LANGUAGE, HttpFields.__separators);
        
        // handle no locale
        if (enm == null || !enm.hasMoreElements())
            return Collections.enumeration(__defaultLocale);
        
        // sort the list in quality order
        List acceptLanguage = HttpFields.qualityList(enm);
        
        if (acceptLanguage.size()==0)
            return
            Collections.enumeration(__defaultLocale);
        
        Object langs = null;
        int size=acceptLanguage.size();
        
        // convert to locals
        for (int i=0; i<size; i++)
        {
            String language = (String)acceptLanguage.get(i);
            language=HttpFields.valueParameters(language,null);
            String country = "";
            int dash = language.indexOf('-');
            if (dash > -1)
            {
                country = language.substring(dash + 1).trim();
                language = language.substring(0,dash).trim();
            }
            langs=LazyList.ensureSize(langs,size);
            langs=LazyList.add(langs,new Locale(language,country));
        }
        
        if (LazyList.size(langs)==0)
            return Collections.enumeration(__defaultLocale);
        
        return Collections.enumeration(LazyList.getList(langs));
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getLocalName()
     */
    public String getLocalName()
    {
        if (_dns)
            return _endp==null?null:_endp.getLocalHost();
        return _endp==null?null:_endp.getLocalAddr();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getLocalPort()
     */
    public int getLocalPort()
    {
        return _endp==null?0:_endp.getLocalPort();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getMethod()
     */
    public String getMethod()
    {
        return _method;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getParameter(java.lang.String)
     */
    public String getParameter(String name)
    {
        if (!_paramsExtracted) 
            extractParameters();
        return (String) _parameters.getValue(name, 0);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getParameterMap()
     */
    public Map getParameterMap()
    {
        if (!_paramsExtracted) 
            extractParameters();
        
        return Collections.unmodifiableMap(_parameters.toStringArrayMap());
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getParameterNames()
     */
    public Enumeration getParameterNames()
    {
        if (!_paramsExtracted) 
            extractParameters();
        return Collections.enumeration(_parameters.keySet());
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getParameterValues(java.lang.String)
     */
    public String[] getParameterValues(String name)
    {
        if (!_paramsExtracted) 
            extractParameters();
        List vals = _parameters.getValues(name);
        if (vals==null)
            return null;
        return (String[])vals.toArray(new String[vals.size()]);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getPathInfo()
     */
    public String getPathInfo()
    {
        return _pathInfo;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getPathTranslated()
     */
    public String getPathTranslated()
    {
        if (_pathInfo==null || _context==null)
            return null;
        return _context.getRealPath(_pathInfo);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getProtocol()
     */
    public String getProtocol()
    {
        return _protocol;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getReader()
     */
    public BufferedReader getReader() throws IOException
    {
        if (_inputState!=__NONE && _inputState!=__READER)
            throw new IllegalStateException("STREAMED");

        if (_inputState==__READER)
            return _reader;
        
        String encoding=getCharacterEncoding();
        if (encoding==null)
            encoding=StringUtil.__ISO_8859_1;
        
        if (_reader==null || !encoding.equalsIgnoreCase(_readerEncoding))
        {
            final ServletInputStream in = getInputStream();
            _readerEncoding=encoding;
            _reader=new BufferedReader(new InputStreamReader(in,encoding))
            {
                public void close() throws IOException
                {
                    in.close();
                }   
            };
        }
        _inputState=__READER;
        return _reader;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getRealPath(java.lang.String)
     */
    public String getRealPath(String path)
    {
        if (_context==null)
            return null;
        return _context.getRealPath(path);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getRemoteAddr()
     */
    public String getRemoteAddr()
    {
        if (_remoteAddr != null)
            return _remoteAddr;	
        return _endp==null?null:_endp.getRemoteAddr();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getRemoteHost()
     */
    public String getRemoteHost()
    {
        if (_dns)
        {
            if (_remoteHost != null)
            {
                return _remoteHost;
            }
            return _endp==null?null:_endp.getRemoteHost();
        }
        return getRemoteAddr();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getRemotePort()
     */
    public int getRemotePort()
    {
        return _endp==null?0:_endp.getRemotePort();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getRemoteUser()
     */
    public String getRemoteUser()
    {
        Principal p = getUserPrincipal();
        if (p==null)
            return null;
        return p.getName();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getRequestDispatcher(java.lang.String)
     */
    public RequestDispatcher getRequestDispatcher(String path)
    {
        if (path == null || _context==null)
            return null;

        // handle relative path
        if (!path.startsWith("/"))
        {
            String relTo=URIUtil.addPaths(_servletPath,_pathInfo);
            int slash=relTo.lastIndexOf("/");
            if (slash>1)
                relTo=relTo.substring(0,slash+1);
            else
                relTo="/";
            path=URIUtil.addPaths(relTo,path);
        }
    
        return _context.getRequestDispatcher(path);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
     */
    public String getRequestedSessionId()
    {
        return _requestedSessionId;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getRequestURI()
     */
    public String getRequestURI()
    {
        if (_requestURI==null && _uri!=null)
            _requestURI=_uri.getPathAndParam();
        return _requestURI;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getRequestURL()
     */
    public StringBuffer getRequestURL()
    {
        StringBuffer url = new StringBuffer(48);
        synchronized (url)
        {
            String scheme = getScheme();
            int port = getServerPort();

            url.append(scheme);
            url.append("://");
            url.append(getServerName());
            if (_port>0 && 
                ((scheme.equalsIgnoreCase(URIUtil.HTTP) && port != 80) || 
                 (scheme.equalsIgnoreCase(URIUtil.HTTPS) && port != 443)))
            {
                url.append(':');
                url.append(_port);
            }
            
            url.append(getRequestURI());
            return url;
        }
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getScheme()
     */
    public String getScheme()
    {
        return _scheme;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getServerName()
     */
    public String getServerName()
    {       
        // Return already determined host
        if (_serverName != null) 
            return _serverName;

        // Return host from absolute URI
        _serverName = _uri.getHost();
        _port = _uri.getPort();
        if (_serverName != null) 
            return _serverName;

        // Return host from header field
        Buffer hostPort = _connection.getRequestFields().get(HttpHeaders.HOST_BUFFER);
        if (hostPort!=null)
        {
            for (int i=hostPort.length();i-->0;)   
            {
                if (hostPort.peek(hostPort.getIndex()+i)==':')
                {
                    _serverName=BufferUtil.to8859_1_String(hostPort.peek(hostPort.getIndex(), i));
                    _port=BufferUtil.toInt(hostPort.peek(hostPort.getIndex()+i+1, hostPort.length()-i-1));
                    return _serverName;
                }
            }
            if (_serverName==null || _port<0)
            {
                _serverName=BufferUtil.to8859_1_String(hostPort);
                _port = 0;
            }
            
            return _serverName;
        }

        // Return host from connection
        if (_connection != null)
        {
            _serverName = getLocalName();
            _port = getLocalPort();
            if (_serverName != null && !Portable.ALL_INTERFACES.equals(_serverName)) 
                return _serverName;
        }

        // Return the local host
        try
        {
            _serverName = InetAddress.getLocalHost().getHostAddress();
        }
        catch (java.net.UnknownHostException e)
        {
            Log.ignore(e);
        }
        return _serverName;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#getServerPort()
     */
    public int getServerPort()
    {
        if (_port<=0)
        {
            if (_serverName==null)
                getServerName();
        
            if (_port<=0)
            {
                if (_serverName!=null && _uri!=null)
                    _port = _uri.getPort();
                else
                    _port = _endp==null?0:_endp.getLocalPort();
            }
        }
        
        if (_port<=0)
        {
            if (getScheme().equalsIgnoreCase(URIUtil.HTTPS))
                return 443;
            return 80;
        }
        return _port;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getServletPath()
     */
    public String getServletPath()
    {
        if (_servletPath==null)
            _servletPath="";
        return _servletPath;
    }
    
    /* ------------------------------------------------------------ */
    /* 
     */
    public String getServletName()
    {
        return _servletName;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getSession()
     */
    public HttpSession getSession()
    {
        return getSession(true);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getSession(boolean)
     */
    public HttpSession getSession(boolean create)
    {
        if (_sessionManager==null && create)
            throw new IllegalStateException("No SessionHandler or SessionManager");
        
        if (_session != null && _sessionManager!=null && _sessionManager.isValid(_session))
            return _session;
        
        _session=null;
        
        String id=getRequestedSessionId();
        
        if (id != null && _sessionManager!=null)
        {
            _session=_sessionManager.getHttpSession(id);
            if (_session == null && !create)
                return null;
        }
        
        if (_session == null && _sessionManager!=null && create )
        {
            _session=_sessionManager.newHttpSession(this);
            Cookie cookie=_sessionManager.getSessionCookie(_session,getContextPath(),isSecure());
            if (cookie!=null)
                _connection.getResponse().addCookie(cookie);
        }
        
        return _session;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
     */
    public Principal getUserPrincipal()
    {
        /* JASPI requires that all requests go through authentication before being processed, so this code should never apply
        if (_userPrincipal != null && _userPrincipal instanceof ConstraintSecurityHandler.NotChecked)
        {
            ConstraintSecurityHandler.NotChecked not_checked=(ConstraintSecurityHandler.NotChecked)_userPrincipal;
            _userPrincipal = ConstraintSecurityHandler.__NO_USER;
            
            Authenticator auth=not_checked.getSecurityHandler().getAuthenticator();
            UserRealm realm=not_checked.getSecurityHandler().getUserRealm();
            String pathInContext=getPathInfo()==null?getServletPath():(getServletPath()+getPathInfo());
            
            if (realm != null && auth != null)
            {
                try
                {
                    auth.authenticate(realm, pathInContext, this, null);
                }
                catch (Exception e)
                {
                    Log.ignore(e);
                }
            }
        }
        */
        
//        if (_userPrincipal == AbstractSecurityHandler.__NO_USER)
//            return null;
        return _userIdentity.getUserPrincipal();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#getQueryString()
     */
    public String getQueryString()
    {
        if (_queryString==null && _uri!=null)
            _queryString=_uri.getQuery(_queryEncoding);
        return _queryString;
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
     */
    public boolean isRequestedSessionIdFromCookie()
    {
        return _requestedSessionId!=null && _requestedSessionIdFromCookie;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromUrl()
     */
    public boolean isRequestedSessionIdFromUrl()
    {
        return _requestedSessionId!=null && !_requestedSessionIdFromCookie;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
     */
    public boolean isRequestedSessionIdFromURL()
    {
        return _requestedSessionId!=null && !_requestedSessionIdFromCookie;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
     */
    public boolean isRequestedSessionIdValid()
    {	
        if (_requestedSessionId==null)
            return false;
        
        HttpSession session=getSession(false);
        return (session==null?false:_sessionManager.getIdManager().getClusterId(_requestedSessionId).equals(_sessionManager.getClusterId(session)));
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#isSecure()
     */
    public boolean isSecure()
    {
        return _connection.isConfidential(this);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.http.HttpServletRequest#isUserInRole(java.lang.String)
     */
    public boolean isUserInRole(String role)
    {
        return _userIdentity.isUserInRole(role);
//        if (_roleMap!=null)
//        {
//            String r=(String)_roleMap.get(role);
//            if (r!=null)
//                role=r;
//        }
//
//        Principal principal = getUserPrincipal();
//
//        if (_userRealm!=null && principal!=null)
//            return _userRealm.isUserInRole(principal, role);
//
//        return false;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#removeAttribute(java.lang.String)
     */
    public void removeAttribute(String name)
    {
        Object old_value=_attributes==null?null:_attributes.getAttribute(name);
        
        if (_attributes!=null)
            _attributes.removeAttribute(name);
        
        if (old_value!=null)
        {
            if (_requestAttributeListeners!=null)
            {
                ServletRequestAttributeEvent event =
                    new ServletRequestAttributeEvent(_context,this,name, old_value);

                for(int i=0;i<LazyList.size(_requestAttributeListeners);i++)
                    ((ServletRequestAttributeListener)LazyList.get(_requestAttributeListeners,i)).attributeRemoved(event);
            }
        }
    }

    /* ------------------------------------------------------------ */
    /* 
     * Set a request attribute.
     * if the attribute name is "org.mortbay.jetty.Request.queryEncoding" then
     * the value is also passed in a call to {@link #setQueryEncoding}.
     * 
     * @see javax.servlet.ServletRequest#setAttribute(java.lang.String, java.lang.Object)
     */
    public void setAttribute(String name, Object value)
    {
        Object old_value=_attributes==null?null:_attributes.getAttribute(name);
        
        if ("org.mortbay.jetty.Request.queryEncoding".equals(name))
            setQueryEncoding(value==null?null:value.toString());
        
        if (_attributes==null)
            _attributes=new AttributesMap();
        _attributes.setAttribute(name, value);
        
        if (_requestAttributeListeners!=null)
        {
            ServletRequestAttributeEvent event =
                new ServletRequestAttributeEvent(_context,this,name, old_value==null?value:old_value);

            for(int i=0;i<LazyList.size(_requestAttributeListeners);i++)
            {
                ServletRequestAttributeListener l = (ServletRequestAttributeListener)LazyList.get(_requestAttributeListeners,i);
                
                if (old_value==null)
                    l.attributeAdded(event);
                else if (value==null)
                    l.attributeRemoved(event);
                else
                    l.attributeReplaced(event);
            }
        }
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#setCharacterEncoding(java.lang.String)
     */
    public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException
    {
        if (_inputState!=__NONE) 
            return;

        _characterEncoding=encoding;
        
        // check encoding is supported
        "".getBytes(encoding);
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.ServletRequest#setCharacterEncoding(java.lang.String)
     */
    public void setCharacterEncodingUnchecked(String encoding)
    {
        _characterEncoding=encoding;
    }
    

    /* ------------------------------------------------------------ */
    /*
     * Extract Paramters from query string and/or form _content.
     */
    private void extractParameters()
    {
        if (_baseParameters == null) 
            _baseParameters = new MultiMap(16);
        
        if (_paramsExtracted) 
        {
            if (_parameters==null)
                _parameters=_baseParameters;
            return;
        }
        
        _paramsExtracted = true;

        // Handle query string
        if (_uri!=null && _uri.getQuery()!=null)
        {
            try
            {
                _uri.decodeQueryTo(_baseParameters,_queryEncoding);
            }
            catch (UnsupportedEncodingException e)
            {
                if (Log.isDebugEnabled())
                    Log.warn(e);
                else
                    Log.warn(e.toString());
            }
        }

        // handle any _content.
        String encoding = getCharacterEncoding();
        String content_type = getContentType();
        if (content_type != null && content_type.length() > 0)
        {
            content_type = HttpFields.valueParameters(content_type, null);
            
            if (MimeTypes.FORM_ENCODED.equalsIgnoreCase(content_type) && HttpMethods.POST.equals(getMethod()))
            {
                int content_length = getContentLength();
                if (content_length != 0)
                {
                    try
                    {
                        int maxFormContentSize=-1;
                        
                        if (_context!=null)
                            maxFormContentSize=_context.getContextHandler().getMaxFormContentSize();
                        else
                        {
                            Integer size = (Integer)_connection.getConnector().getServer().getAttribute("org.mortbay.jetty.Request.maxFormContentSize");
                            if (size!=null)
                                maxFormContentSize =size.intValue();
                        }
                        
                        if (content_length>maxFormContentSize && maxFormContentSize > 0)
                        {
                            throw new IllegalStateException("Form too large"+content_length+">"+maxFormContentSize);
                        }
                        InputStream in = getInputStream();
                       
                        // Add form params to query params
                        UrlEncoded.decodeTo(in, _baseParameters, encoding,content_length<0?maxFormContentSize:-1);
                    }
                    catch (IOException e)
                    {
                        if (Log.isDebugEnabled())
                            Log.warn(e);
                        else
                            Log.warn(e.toString());
                    }
                }
            }
        }
        
        if (_parameters==null)
            _parameters=_baseParameters;
        else if (_parameters!=_baseParameters)
        {
            // Merge parameters (needed if parameters extracted after a forward).
            Iterator iter = _baseParameters.entrySet().iterator();
            while (iter.hasNext())
            {
                Map.Entry entry = (Map.Entry)iter.next();
                String name=(String)entry.getKey();
                Object values=entry.getValue();
                for (int i=0;i<LazyList.size(values);i++)
                    _parameters.add(name, LazyList.get(values, i));
            }
        }   
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param host The host to set.
     */
    public void setServerName(String host)
    {
        _serverName = host;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param port The port to set.
     */
    public void setServerPort(int port)
    {
        _port = port;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param addr The address to set.
     */
    public void setRemoteAddr(String addr)
    {
        _remoteAddr = addr;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param host The host to set.
     */
    public void setRemoteHost(String host)
    {
        _remoteHost = host;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the uri.
     */
    public HttpURI getUri()
    {
        return _uri;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param uri The uri to set.
     */
    public void setUri(HttpURI uri)
    {
        _uri = uri;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the connection.
     */
    public HttpConnection getConnection()
    {
        return _connection;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the inputState.
     */
    public int getInputState()
    {
        return _inputState;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param authType The authType to set.
     */
    public void setAuthType(String authType)
    {
        _authType = authType;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param cookies The cookies to set.
     */
    public void setCookies(Cookie[] cookies)
    {
        if (_cookies==null)
            _cookies=new CookieCutter();
        _cookies.setCookies(cookies);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param method The method to set.
     */
    public void setMethod(String method)
    {
        _method = method;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param pathInfo The pathInfo to set.
     */
    public void setPathInfo(String pathInfo)
    {
        _pathInfo = pathInfo;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param protocol The protocol to set.
     */
    public void setProtocol(String protocol)
    {
        _protocol = protocol;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param requestedSessionId The requestedSessionId to set.
     */
    public void setRequestedSessionId(String requestedSessionId)
    {
        _requestedSessionId = requestedSessionId;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the sessionManager.
     */
    public SessionManager getSessionManager()
    {
        return _sessionManager;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param sessionManager The sessionManager to set.
     */
    public void setSessionManager(SessionManager sessionManager)
    {
        _sessionManager = sessionManager;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param requestedSessionIdCookie The requestedSessionIdCookie to set.
     */
    public void setRequestedSessionIdFromCookie(boolean requestedSessionIdCookie)
    {
        _requestedSessionIdFromCookie = requestedSessionIdCookie;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param session The session to set.
     */
    public void setSession(HttpSession session)
    {
        _session = session;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param scheme The scheme to set.
     */
    public void setScheme(String scheme)
    {
        _scheme = scheme;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param queryString The queryString to set.
     */
    public void setQueryString(String queryString)
    {
        _queryString = queryString;
    }
    /* ------------------------------------------------------------ */
    /**
     * @param requestURI The requestURI to set.
     */
    public void setRequestURI(String requestURI)
    {
        _requestURI = requestURI;
    }
    /* ------------------------------------------------------------ */
    /**
     * Sets the "context path" for this request
     * @see HttpServletRequest#getContextPath
     */
    public void setContextPath(String contextPath)
    {
        _contextPath = contextPath;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param servletPath The servletPath to set.
     */
    public void setServletPath(String servletPath)
    {
        _servletPath = servletPath;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param name The servletName to set.
     */
    public void setServletName(String name)
    {
        _servletName = name;
    }
    
    /* ------------------------------------------------------------ */

    public UserIdentity getUserIdentity()
    {
        return _userIdentity;
    }

    public void setUserIdentity(UserIdentity userIdentity)
    {
        if (userIdentity == null)
            throw new NullPointerException("No UserIdentity");
        this._userIdentity = userIdentity;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param context
     */
    public void setContext(SContext context)
    {
        _context=context;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The current {@link SContext context} used for this request, or <code>null</code> if {@link #setContext} has not yet
     * been called. 
     */
    public SContext getContext()
    {
        return _context;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * Reconstructs the URL the client used to make the request. The returned URL contains a
     * protocol, server name, port number, and, but it does not include a path.
     * <p>
     * Because this method returns a <code>StringBuffer</code>, not a string, you can modify the
     * URL easily, for example, to append path and query parameters.
     * 
     * This method is useful for creating redirect messages and for reporting errors.
     * 
     * @return "scheme://host:port"
     */
    public StringBuilder getRootURL()
    {
        StringBuilder url = new StringBuilder(48);
        String scheme = getScheme();
        int port = getServerPort();

        url.append(scheme);
        url.append("://");
        url.append(getServerName());

        if (port > 0 && ((scheme.equalsIgnoreCase("http") && port != 80) || (scheme.equalsIgnoreCase("https") && port != 443)))
        {
            url.append(':');
            url.append(port);
        }
        return url;
    }

    /* ------------------------------------------------------------ */
    /* 
     */
    public Attributes getAttributes()
    {
        if (_attributes==null)
            _attributes=new AttributesMap();
        return _attributes;
    }
    
    /* ------------------------------------------------------------ */
    /* 
     */
    public void setAttributes(Attributes attributes)
    {
        _attributes=attributes;
    }

    /* ------------------------------------------------------------ */
    /**
     * @deprecated
     */
    public Continuation getContinuation()
    {
        return _continuation;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @deprecated
     */
    public Continuation getContinuation(boolean create)
    {
        if (_continuation==null && create)
            _continuation=new Servlet3Continuation(this); 
        return _continuation;
    }
    
    /* ------------------------------------------------------------ */
    void setContinuation(Continuation cont)
    {
        _continuation=cont;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the parameters.
     */
    public MultiMap getParameters()
    {
        return _parameters;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param parameters The parameters to set.
     */
    public void setParameters(MultiMap parameters)
    {
        _parameters= (parameters==null)?_baseParameters:parameters;
        if (_paramsExtracted && _parameters==null)
            throw new IllegalStateException();
    }
    
    /* ------------------------------------------------------------ */
    public String toString()
    {
        return (_handled?"[":"(")+getMethod()+" "+_uri+(_handled?"]@":")@")+hashCode()+" "+super.toString();
    }

    /* ------------------------------------------------------------ */
    public static Request getRequest(HttpServletRequest request)
    {
        if (request instanceof Request)
            return (Request) request;
        
        while (request instanceof ServletRequestWrapper)
            request = (HttpServletRequest)((ServletRequestWrapper)request).getRequest();
        
        if (request instanceof Request)
            return (Request) request;
        
        return HttpConnection.getCurrentConnection().getRequest();
    }
    

    /* ------------------------------------------------------------ */
    public synchronized void addEventListener(EventListener listener) 
    {
        if (listener instanceof ServletRequestAttributeListener)
            _requestAttributeListeners= LazyList.add(_requestAttributeListeners, listener);
    }
    
    /* ------------------------------------------------------------ */
    public synchronized void removeEventListener(EventListener listener) 
    {
        _requestAttributeListeners= LazyList.remove(_requestAttributeListeners, listener);
    }

    /* ------------------------------------------------------------ */
    public void saveNewSession(Object key,HttpSession session)
    {
        if (_savedNewSessions==null)
            _savedNewSessions=new HashMap();
        _savedNewSessions.put(key,session);
    }
    /* ------------------------------------------------------------ */
    public HttpSession recoverNewSession(Object key)
    {
        if (_savedNewSessions==null)
            return null;
        return (HttpSession) _savedNewSessions.get(key);
    }

    /* ------------------------------------------------------------ */
    public String getQueryEncoding()
    {
        return _queryEncoding;
    }

    /* ------------------------------------------------------------ */
    /** Set the character encoding used for the query string.
     * This call will effect the return of getQueryString and getParamaters.
     * It must be called before any geParameter methods.
     * 
     * The request attribute "org.mortbay.jetty.Request.queryEncoding"
     * may be set as an alternate method of calling setQueryEncoding.
     * 
     * @param queryEncoding
     */
    public void setQueryEncoding(String queryEncoding)
    {
        _queryEncoding=queryEncoding;
        _queryString=null;
    }

    /* ------------------------------------------------------------ */
    public void suspend()
    {
        long timeout = 30000L;
        if (_context!=null)
        {
            Long t=(Long)_context.getAttribute("javax.servlet.suspendTimeoutMs");
            if (t!=null)
                timeout=t.longValue();
        }
        suspend(timeout);
    }
    
    /* ------------------------------------------------------------ */
    public void complete() throws IOException
    {
        try
        {
            _connection.getResponse().flushBuffer();
        }
        finally
        {
            super.complete();
        }
    }
    
    /* ------------------------------------------------------------ */
    public ServletContext getServletContext()
    {
        return _context;
    }

    /* ------------------------------------------------------------ */
    public ServletResponse getServletResponse()
    {
        return _connection.getResponse();
    }
    
}

