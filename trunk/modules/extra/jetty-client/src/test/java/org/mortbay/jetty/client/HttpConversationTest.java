// ========================================================================
// Copyright 2006-2007 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.mortbay.jetty.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.mortbay.io.Buffer;
import org.mortbay.io.ByteArrayBuffer;
import org.mortbay.jetty.*;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.security.*;
import org.mortbay.jetty.client.security.BasicAuthentication;
import org.mortbay.jetty.client.security.SecurityRealm;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;

/**
 * Functional testing for HttpExchange.
 *
 * @author Matthew Purland
 * @author Greg Wilkins
 */
public class HttpConversationTest extends TestCase
{
    private Server _server;
    private int _port;
    private HttpClient _httpClient;

    protected void setUp() throws Exception
    {
        startServer();
        _httpClient=new HttpClient();
        _httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        _httpClient.setMaxConnectionsPerAddress(2);
        _httpClient.start();
    }

    protected void tearDown() throws Exception
    {
        stopServer();
        _httpClient.stop();
    }

    public void xtestPerf() throws Exception
    {
        sender(1);
        Thread.sleep(200);
        sender(10);
        Thread.sleep(200);
        sender(100);
        Thread.sleep(200);
        sender(1000);
        Thread.sleep(200);
        sender(10000);
    }

    /**
     * Test sending data through the exchange.
     *
     * @throws java.io.IOException
     */
    public void sender(final int nb) throws Exception
    {
        final CountDownLatch latch=new CountDownLatch(nb);
        long l0=System.currentTimeMillis();
        for (int i=0; i<nb; i++)
        {
            final int n=i;
            if (n%1000==0)
            {
                Thread.sleep(200);
            }

            HttpExchange httpExchange=new HttpExchange()
            {
                protected void onRequestCommitted()
                {
                    // System.err.println("Request committed");
                }

                protected void onResponseStatus(Buffer version, int status, Buffer reason)
                {
                    // System.err.println("Response Status: " + version+" "+status+" "+reason);
                }

                protected void onResponseHeader(Buffer name, Buffer value)
                {
                    // System.err.println("Response header: " + name + " = " + value);
                }

                protected void onResponseContent(Buffer content)
                {
                    // System.err.println("Response content:" + content);
                }

                protected void onResponseComplete()
                {
                    // System.err.println("Response completed "+n);
                    latch.countDown();
                }

            };

            httpExchange.setURL("http://localhost:"+_port+"/");
            httpExchange.addRequestHeader("arbitrary","value");

            _httpClient.send(httpExchange);
        }

        long last=latch.getCount();
        while(last>0)
        {
            // System.err.println("waiting for "+last+" sent "+(System.currentTimeMillis()-l0)/1000 + "s ago ...");
            latch.await(5,TimeUnit.SECONDS);
            long next=latch.getCount();
            if (last==next)
                break;
            last=next;
        }
        // System.err.println("missed "+latch.getCount()+" sent "+(System.currentTimeMillis()-l0)/1000 + "s ago.");
        assertEquals(0,latch.getCount());
        long l1=System.currentTimeMillis();
    }

    public void testGetWithContentExchange() throws Exception
    {
        int i = 1;

        HttpExchange.ContentExchange httpExchange = new HttpExchange.ContentExchange();
        httpExchange.setURL("http://localhost:" + _port + "/?i=" + i);
        httpExchange.setMethod(HttpMethods.GET);

        HttpConversation wrapper = new HttpConversation(httpExchange)
        {
            public void success()
            {
                assertTrue(true);
            }

            public void failure()
            {
                assertTrue(false);
            }

            public void failure(Throwable t)
            {
                t.printStackTrace();
                assertTrue(false);
            }
        };
        wrapper.addAuthentication(new BasicAuthentication());
        wrapper.addSecurityRealm(new SecurityRealm()
        {
            public String getId()
            {
                return "test";
            }

            public String getPrincipal()
            {
                return "jetty";
            }

            public String getCredentials()
            {
                return "jetty";
            }
        });

        _httpClient.send(wrapper);
                   wrapper.waitForSuccess();
        assertTrue( wrapper.waitForSuccess() );
        Thread.sleep(10);

    }


    public void testGetWithContentExchangeWithMultipleRealms() throws Exception
    {
        int i = 1;

        HttpExchange.ContentExchange httpExchange = new HttpExchange.ContentExchange();
        httpExchange.setURL("http://localhost:" + _port + "/?i=" + i);
        httpExchange.setMethod(HttpMethods.GET);

        HttpConversation wrapper = new HttpConversation(httpExchange)
        {
            public void success()
            {
                assertTrue(true);
            }

            public void failure()
            {
                assertTrue(false);
            }

            public void failure(Throwable t)
            {
                assertTrue(false);
            }
        };
        wrapper.addAuthentication(new BasicAuthentication());
        wrapper.addSecurityRealm(new SecurityRealm()
        {
            public String getId()
            {
                return "test1";
            }

            public String getPrincipal()
            {
                return "jetty";
            }

            public String getCredentials()
            {
                return "bad";
            }
        });
        wrapper.addSecurityRealm(new SecurityRealm()
        {
            public String getId()
            {
                return "test2";
            }

            public String getPrincipal()
            {
                return "jetty";
            }

            public String getCredentials()
            {
                return "jetty";
            }
        });

        _httpClient.send(wrapper);

        assertTrue( wrapper.waitForSuccess() );
        Thread.sleep(10);

    }

    public static void copyStream(InputStream in, OutputStream out)
    {
        try
        {
            byte[] buffer=new byte[1024];
            int len;
            while ((len=in.read(buffer))>=0)
            {
                out.write(buffer,0,len);
            }
        }
        catch (EofException e)
        {
            System.err.println(e);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void startServer() throws Exception
     {
         _server = new Server();
         _server.setGracefulShutdown(500);
         Connector connector = new SelectChannelConnector();

         connector.setPort(0);
         _server.setConnectors(new Connector[]{connector});

         UserRealm userRealm = new HashUserRealm("MyRealm", "src/test/resources/realm.properties");

         Constraint constraint = new Constraint();
         constraint.setName("Need User or Admin");
         constraint.setRoles(new String[]{"user", "admin"});
         constraint.setAuthenticate(true);

         ConstraintMapping cm = new ConstraintMapping();
         cm.setConstraint(constraint);
         cm.setPathSpec("/*");

         SecurityHandler sh = new SecurityHandler();
         _server.setHandler(sh);
         sh.setUserRealm(userRealm);
         sh.setConstraintMappings(new ConstraintMapping[]{cm});
         sh.setAuthenticator(new BasicAuthenticator());

         Handler testHandler = new AbstractHandler()
         {

             public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException
             {
                 System.out.println("passed authentication!");
                 Request base_request=(request instanceof Request)?(Request)request:HttpConnection.getCurrentConnection().getRequest();
                 base_request.setHandled(true);
                 response.setStatus(200);
                 if (request.getServerName().equals("jetty.mortbay.org"))
                 {
                     response.getOutputStream().println("Proxy request: "+request.getRequestURL());
                 }
                 else if (request.getMethod().equalsIgnoreCase("GET"))
                 {
                     response.getOutputStream().println("<hello>");
                     for (int i=0; i<100; i++)
                     {
                         response.getOutputStream().println("  <world>"+i+"</world>");
                         if (i%20==0)
                             response.getOutputStream().flush();
                     }
                     response.getOutputStream().println("</hello>");
                 }
                 else
                 {
                     copyStream(request.getInputStream(),response.getOutputStream());
                 }
             }
         };

         sh.setHandler(testHandler);

         _server.start();
         _port = connector.getLocalPort();
     }


    private void stopServer() throws Exception
    {
        _server.stop();
    }
}