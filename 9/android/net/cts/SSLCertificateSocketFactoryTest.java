/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.cts;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLPeerUnverifiedException;

import android.net.SSLCertificateSocketFactory;
import android.test.AndroidTestCase;

import dalvik.annotation.BrokenTest;
import dalvik.annotation.TestLevel;
import dalvik.annotation.TestTargetClass;
import dalvik.annotation.TestTargetNew;
import dalvik.annotation.TestTargets;
import dalvik.annotation.ToBeFixed;

@TestTargetClass(SSLCertificateSocketFactory.class)
public class SSLCertificateSocketFactoryTest extends AndroidTestCase {
    private SSLCertificateSocketFactory mFactory;
    private int mTimeout;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTimeout = 1000;
        mFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(mTimeout);
    }

    @TestTargets({
        @TestTargetNew(
            level = TestLevel.PARTIAL,
            method = "getSupportedCipherSuites",
            args = {}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "getDefault",
            args = {int.class}
        ),
        @TestTargetNew(
            level = TestLevel.PARTIAL,
            method = "getDefaultCipherSuites",
            args = {}
        )
    })
    @ToBeFixed(bug="1695243", explanation="Android API javadocs are incomplete")
    public void testAccessProperties() throws Exception {
        mFactory.getSupportedCipherSuites();
        mFactory.getDefaultCipherSuites();
        SocketFactory sf = SSLCertificateSocketFactory.getDefault(mTimeout);
        assertNotNull(sf);
    }

    @TestTargets({
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "createSocket",
            args = {java.net.InetAddress.class, int.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "createSocket",
            args = {java.net.Socket.class, java.lang.String.class, int.class, boolean.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "createSocket",
            args = {java.lang.String.class, int.class}
        ),
        @TestTargetNew(
            level = TestLevel.NOT_FEASIBLE,
            method = "createSocket",
            args = {java.lang.String.class, int.class, java.net.InetAddress.class, int.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "createSocket",
            args = {java.net.InetAddress.class, int.class, java.net.InetAddress.class, int.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "SSLCertificateSocketFactory",
            args = {int.class}
        )
    })
    @BrokenTest("flaky")
    public void testCreateSocket() throws Exception {
        new SSLCertificateSocketFactory(100);
        int port = 443;
        String host = "www.fortify.net";
        InetAddress inetAddress = null;
        inetAddress = InetAddress.getLocalHost();
        try {
            mFactory.createSocket(inetAddress, port);
            fail("should throw exception!");
        } catch (IOException e) {
            // expected
        }

        try {
            InetAddress inetAddress1 = InetAddress.getLocalHost();
            InetAddress inetAddress2 = InetAddress.getLocalHost();
            mFactory.createSocket(inetAddress1, port, inetAddress2, port);
            fail("should throw exception!");
        } catch (IOException e) {
            // expected
        }

        try {
            Socket socket = new Socket();
            mFactory.createSocket(socket, host, port, true);
            fail("should throw exception!");
        } catch (IOException e) {
            // expected
        }
        Socket socket = null;
        socket = mFactory.createSocket(host, port);
        assertNotNull(socket);
        assertNotNull(socket.getOutputStream());
        assertNotNull(socket.getInputStream());

        // it throw exception when calling createSocket(String, int, InetAddress, int)
        // The socket level is invalid.
    }

    // a host and port that are expected to be available but have
    // a cert with a different CN, in this case CN=mtalk.google.com
    private static String TEST_CREATE_SOCKET_HOST = "mobile-gtalk.l.google.com";
    private static int TEST_CREATE_SOCKET_PORT = 5228;

    /**
     * b/2807618 Make sure that hostname verifcation in cases were it
     * is documented to be included by various
     * SSLCertificateSocketFactory.createSocket messages.
     *
     * NOTE: Test will fail if external server is not available.
     */
    @TestTargetNew(
        level = TestLevel.COMPLETE,
        method = "createSocket",
        args = {String.class, int.class}
    )
    public void test_createSocket_simple() throws Exception {
        try {
            mFactory.createSocket(TEST_CREATE_SOCKET_HOST, TEST_CREATE_SOCKET_PORT);
            fail();
        } catch (SSLPeerUnverifiedException expected) {
            // expected
        }
    }

    /**
     * b/2807618 Make sure that hostname verifcation in cases were it
     * is documented to be included by various
     * SSLCertificateSocketFactory.createSocket messages.
     *
     * NOTE: Test will fail if external server is not available.
     */
    @TestTargetNew(
        level = TestLevel.COMPLETE,
        method = "createSocket",
        args = {Socket.class, String.class, int.class, boolean.class}
    )
    public void test_createSocket_wrapping() throws Exception {
        try {
            Socket underlying = new Socket(TEST_CREATE_SOCKET_HOST, TEST_CREATE_SOCKET_PORT);
            mFactory.createSocket(
                    underlying, TEST_CREATE_SOCKET_HOST, TEST_CREATE_SOCKET_PORT, true);
            fail();
        } catch (SSLPeerUnverifiedException expected) {
            // expected
        }
    }

    /**
     * b/2807618 Make sure that hostname verifcation in cases were it
     * is documented to be included by various
     * SSLCertificateSocketFactory.createSocket messages.
     *
     * NOTE: Test will fail if external server is not available.
     */
    @TestTargetNew(
        level = TestLevel.COMPLETE,
        method = "createSocket",
        args = {String.class, int.class, InetAddress.class, int.class}
    )
    public void test_createSocket_bind() throws Exception {
        try {
            mFactory.createSocket(TEST_CREATE_SOCKET_HOST, TEST_CREATE_SOCKET_PORT, null, 0);
            fail();
        } catch (SSLPeerUnverifiedException expected) {
            // expected
        }
    }
}
