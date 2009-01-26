package org.mortbay.jetty;

//========================================================================
//Copyright 2004-2008 Mort Bay Consulting Pty. Ltd.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.mortbay.io.Buffer;
import org.mortbay.io.ByteArrayBuffer;

import junit.framework.TestCase;

public class AbstractBuffersTest
    extends TestCase
{
    private int _headerBufferSize = 6 * 1024;

    InnerAbstractBuffers buffers;

    List<Thread> threadList = new ArrayList<Thread>();

    int numThreads = 100;

    int runTestLength = 5000;

    int threadWaitTime = 5;

    boolean runTest = false;

    AtomicLong buffersRetrieved;

    private static int __LOCAL = 1;

    private static int __LIST = 2;

    private static int __QUEUE = 3;

    protected void setUp()
        throws Exception
    {
        super.setUp();
    }

    protected void tearDown()
        throws Exception
    {
        super.tearDown();
    }

    public void execAbstractBuffer( int type )
        throws Exception
    {
        threadList.clear();
        buffersRetrieved = new AtomicLong( 0 );
        buffers = new InnerAbstractBuffers( type );

        for ( int i = 0; i < numThreads; ++i )
        {
            threadList.add( new BufferPeeper( "BufferPeeper: " + i ) );
        }

        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        long mem0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        runTest = true;

        Thread.sleep( runTestLength );

        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        long mem1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        runTest = false;

        long totalBuffersRetrieved = buffersRetrieved.get();

        System.out.println( "Buffers Retrieved: " + totalBuffersRetrieved );
        System.out.println( "Memory Used: " + ( mem1 - mem0 ) );

        for ( Iterator<Thread> i = threadList.iterator(); i.hasNext(); )
        {
            Thread t = i.next();
            t.stop();
        }
    }

    public void testThreadLocalAbstractBuffers()
        throws Exception
    {
        System.out.println( "Thread Local Test" );
        execAbstractBuffer( __LOCAL );

    }

    public void testListAbstractBuffers()
        throws Exception
    {
        System.out.println( "List Test" );
        execAbstractBuffer( __LIST );

    }

    public void testAbstractQueueBuffers()
        throws Exception
    {
        System.out.println( "Queue Test" );
        execAbstractBuffer( __QUEUE );
    }

    /**
     * wrapper for testing different types of AbstractBuffers
     * 
     * @author jesse
     */
    private class InnerAbstractBuffers
    {
        AbstractBuffers abuf;

        public InnerAbstractBuffers( int type )
            throws Exception
        {
            if ( type == __LIST )
            {
                abuf = new InnerListAbstractBuffers();
            }
            else if ( type == __QUEUE )
            {
                abuf = new InnerQueueAbstractBuffers();
            }
            else if ( type == __LOCAL )
            {
                abuf = new InnerThreadLocalAbstractBuffers();
            }
            abuf.start();

            if ( abuf == null )
            {
                throw new IllegalArgumentException( "failed to init buffers" );
            }
            abuf.doStart();
        }

        public Buffer getBuffer( int size )
        {

            Buffer b = abuf.getBuffer( size );

            return b;
        }

        public void returnBuffer( Buffer buffer )
        {
            abuf.returnBuffer( buffer );
        }
    }

    class InnerListAbstractBuffers
        extends ListAbstractBuffers
    {

        public Buffer newBuffer( int size )
        {
            return new ByteArrayBuffer( size );
        }

    }

    class InnerQueueAbstractBuffers
        extends QueueAbstractBuffers
    {

        public Buffer newBuffer( int size )
        {
            return new ByteArrayBuffer( size );
        }

    }

    class InnerThreadLocalAbstractBuffers
        extends ThreadLocalAbstractBuffers
    {

        public Buffer newBuffer( int size )
        {
            return new ByteArrayBuffer( size );
        }

    }

    /**
     * generic buffer peeper
     * 
     * @author jesse
     */
    class BufferPeeper
        extends Thread
    {
        private String _bufferName;

        public BufferPeeper( String bufferName )
        {
            _bufferName = bufferName;

            start();
        }

        public void run()
        {
            while ( true )
            {
                try
                {

                    if ( runTest )
                    {
                        Buffer buf = buffers.getBuffer( _headerBufferSize );

                        buffersRetrieved.getAndIncrement();
                        

                        buf.put( new Byte( "2" ).byteValue() );

                        // sleep( threadWaitTime );

                        buffers.returnBuffer( buf );
                    }
                    else
                    {
                        sleep( 1 );
                    }
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

}
