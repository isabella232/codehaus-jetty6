//========================================================================
// Copyright 2006 Mort Bay Consulting Pty. Ltd.
// expecting other bits copyrighted sun
//------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//========================================================================

package org.mortbay.jetty.grizzly;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.io.Buffer;
import org.mortbay.io.EndPoint;
import org.mortbay.io.nio.ChannelEndPoint;
import org.mortbay.io.nio.NIOBuffer;
import org.mortbay.jetty.AbstractConnector;
import org.mortbay.jetty.EofException;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.HttpException;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.NIOConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.log.Log;
import org.mortbay.util.ajax.Continuation;

/* ------------------------------------------------------------------------------- */
/**
 * @author gregw
 *
 */
public class GrizzlyConnector extends AbstractConnector implements NIOConnector
{
    static Random random = new Random(System.currentTimeMillis());
	
    /* ------------------------------------------------------------------------------- */
    /**
     * Constructor.
     * 
     */
    public GrizzlyConnector()
    {
    }

    /* ------------------------------------------------------------ */
    public Object getConnection()
    {
    	// TODO return private connection eg server socket
        return null;
    }
    


    /* ------------------------------------------------------------ */
    /*
     * @see org.mortbay.jetty.AbstractConnector#doStart()
     */
    protected void doStart() throws Exception
    {
        super.doStart();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.mortbay.jetty.AbstractConnector#doStop()
     */
    protected void doStop() throws Exception
    {
        super.doStop();
    }


    /* ------------------------------------------------------------ */
    public void open() throws IOException
    {
    	// TODO Open server socket
    }

    /* ------------------------------------------------------------ */
    public void close() throws IOException
    {
    	// TODO Close server socket
    }

    /* ------------------------------------------------------------ */
    public void accept(int acceptorID) throws IOException
    {
        // TODO accept a connection for real or progress an existing one
        // but for now.... let's just pretend.
        
        // This creates a connection and runs it to completion.
        // in reality there will be lots of endpoints in play and any one
        // of them may need to be dispatched.
        
        try 
        {
            File file = new File("fakeRequests.txt");
            if (!file.exists())
                file = new File("modules/grizzly/fakeRequests.txt");
            if (!file.exists())
                file = new File("/tmp/fakeRequests.txt");
            if (!file.exists())
            {
                System.err.println("No such file "+file);
                System.exit(1);
            }
            
            Thread.sleep(random.nextInt(5000));
            
            ByteChannel channel = new FileInputStream(file).getChannel();
            GrizzlyEndPoint gep = new GrizzlyEndPoint(this,channel);
            
            try
            {
                System.err.println("new "+gep+ " for "+channel);
                
                // TODO in reality this dispatches would be mixed with others from other connections.
                while (gep.isOpen())
                {
                    Thread.sleep(random.nextInt(1000));
                    if (!gep.dispatched)
                    { 
                        gep.dispatched=true;
                        getThreadPool().dispatch(gep);
                    }
                }
            }
            finally
            {
                System.err.println("end "+gep);
            }
            
        } 
        catch (InterruptedException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    /* ------------------------------------------------------------------------------- */
    protected Buffer newBuffer(int size)
    {
        // TODO
        // Header buffers always byte array buffers (efficiency of random access)
        // There are lots of things to consider here... DIRECT buffers are faster to
        // send but more expensive to build and access! so we have choices to make...
        // + headers are constructed bit by bit and parsed bit by bit, so INDiRECT looks
        // good for them.   
        // + but will a gather write of an INDIRECT header with a DIRECT body be any good?
        // this needs to be benchmarked.
        // + Will it be possible to get a DIRECT header buffer just for the gather writes of
        // content from file mapped buffers?  
        // + Are gather writes worth the effort?  Maybe they will work well with two INDIRECT
        // buffers being copied into a single kernel buffer?
        // 
        if (size==getHeaderBufferSize())
            return new NIOBuffer(size, NIOBuffer.INDIRECT);
        return new NIOBuffer(size, NIOBuffer.DIRECT);
    }

    /* ------------------------------------------------------------------------------- */
    public void customize(EndPoint endpoint, Request request) throws IOException
    {
        super.customize(endpoint, request);
    }


    /* ------------------------------------------------------------------------------- */
    public int getLocalPort()
    {
    	// TODO return the actual port we are listening on
    	return 0;
    }
    

    /* ------------------------------------------------------------------------------- */
    /* ------------------------------------------------------------------------------- */
    /* ------------------------------------------------------------------------------- */
    public static class GrizzlyEndPoint extends ChannelEndPoint implements EndPoint, Runnable
    {
        HttpConnection _connection;
        boolean dispatched=false;
        
        public GrizzlyEndPoint(GrizzlyConnector connector, ByteChannel channel)
        {
            super(channel);
            _connection = new HttpConnection(connector,this,connector.getServer());
        }

        public void run()
        {
            try
            {
                System.err.println("dispatched "+this);
                _connection.handle();
            }
            catch (ClosedChannelException e)
            {
                Log.ignore(e);
            }
            catch (EofException e)
            {
                Log.debug("EOF", e);
                try{close();}
                catch(IOException e2){Log.ignore(e2);}
            }
            catch (HttpException e)
            {
                Log.debug("BAD", e);
                try{close();}
                catch(IOException e2){Log.ignore(e2);}
            }
            catch (Throwable e)
            {
                Log.warn("handle failed", e);
                try{close();}
                catch(IOException e2){Log.ignore(e2);}
            }
            finally
            {
                Continuation continuation =  _connection.getRequest().getContinuation();
                if (continuation != null && continuation.isPending())
                {
                    // We have a continuation
                    // TODO something!
                }
                else
                {
                    dispatched=false;
                    // something else... normally re-enable this connection is the selectset with the latest interested ops
                }
            }
        
        }

        public void blockReadable(long millisecs)
        {
            try {Thread.sleep(random.nextInt(1000));} catch (InterruptedException e) {e.printStackTrace();}
        }

        public void blockWritable(long millisecs)
        {
            try {Thread.sleep(random.nextInt(1000));} catch (InterruptedException e) {e.printStackTrace();}
        }

        public int fill(Buffer buffer) throws IOException
        {
            // sometimes read nothing
            if (random.nextInt()%10 <2 )
                return 0;
            
            // Get a random amount of data
            int len=random.nextInt(2000);
            if (len>buffer.space())
                len=buffer.space();
            
            // Load a length limited slice via a temp buffer
            NIOBuffer temp= new NIOBuffer(len,false);
            int len2=super.fill(temp);
            if (len2<0)
                return -1;
            
            assert len==len2;
            buffer.put(temp);
            
            return len;
        }

        public int flush(Buffer header, Buffer buffer, Buffer trailer) throws IOException
        {
            int len=0;
            
            // TODO gather operation.
            if (header!=null && header.hasContent())
                len+=flush(header);
            
            if (header==null || !header.hasContent())
            {
                if (buffer!=null && buffer.hasContent())
                    len+=flush(buffer);
            }

            if (buffer==null || !buffer.hasContent())
            {
                if (trailer!=null && trailer.hasContent())
                    len+=flush(trailer);
            }
            
            return len;
            
        }

        public int flush(Buffer buffer) throws IOException
        {
            // sometimes flush nothing
            if (random.nextInt(10) <2 )
                return 0;

            // flush a random amount of data
            int len=random.nextInt(2000);
            if (len>buffer.length())
                len=buffer.length();
            
            // Load a length limited slice via a temp buffer
            Buffer temp= buffer.get(len);
            System.err.println("flush:"+temp);
            return len;
        }

        public boolean isBlocking()
        {
            return false;
        }
        
    }
    
    public static void main(String[] arg)
        throws Exception
    {
        // Just a little test main....

        Server server = new Server();
        server.addConnector(new GrizzlyConnector());
        Context context = new Context(server,"/",Context.SESSIONS);
        context.addServlet(new ServletHolder(new HelloServlet()), "/*");
        
        server.start();
        server.join();
    }
    
    public static class HelloServlet extends HttpServlet
    {
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            System.err.println(request);
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter out =  response.getWriter();
            out.println("<h1>Hello SimpleServlet: "+request.getRequestURI()+"</h1><pre>");
            int lines = random.nextInt(100);
            for (int i=0;i<lines;i++)
                out.println(i+" Blah blah blah");
            out.println("</pre>");       
        }
    }
    
}
