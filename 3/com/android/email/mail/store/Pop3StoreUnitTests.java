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

package com.android.email.mail.store;

import com.android.email.mail.Address;
import com.android.email.mail.FetchProfile;
import com.android.email.mail.Flag;
import com.android.email.mail.Folder;
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Transport;
import com.android.email.mail.Folder.FolderType;
import com.android.email.mail.Folder.OpenMode;
import com.android.email.mail.Message.RecipientType;
import com.android.email.mail.internet.BinaryTempFileBody;
import com.android.email.mail.internet.MimeMessage;
import com.android.email.mail.transport.MockTransport;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * This is a series of unit tests for the POP3 Store class.  These tests must be locally
 * complete - no server(s) required.
 */
@SmallTest
public class Pop3StoreUnitTests extends AndroidTestCase {

    final String UNIQUE_ID_1 = "20080909002219r1800rrjo9e00";
    
    final static int PER_MESSAGE_SIZE = 100;
    
    /* These values are provided by setUp() */
    private Pop3Store mStore = null;
    private Pop3Store.Pop3Folder mFolder = null;
    
    /**
     * Setup code.  We generate a lightweight Pop3Store and Pop3Store.Pop3Folder.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        // These are needed so we can get at the inner classes
        mStore = new Pop3Store("pop3://user:password@server:999");
        mFolder = (Pop3Store.Pop3Folder) mStore.getFolder("INBOX");
        
        // This is needed for parsing mime messages
        BinaryTempFileBody.setTempDirectory(this.getContext().getCacheDir());
    }

    /**
     * Test various sunny-day operations of UIDL parser for multi-line responses
     */
    public void testUIDLParserMulti() {

        // multi-line mode
        Pop3Store.Pop3Folder.UidlParser parser = mFolder.new UidlParser();
        
        // Test basic in-list UIDL
        parser.parseMultiLine("101 " + UNIQUE_ID_1);
        assertEquals(101, parser.mMessageNumber);
        assertEquals(UNIQUE_ID_1, parser.mUniqueId);
        assertFalse(parser.mEndOfMessage);
        assertFalse(parser.mErr);
        
        //  Test end-of-list
        parser.parseMultiLine(".");
        assertTrue(parser.mEndOfMessage);
        assertFalse(parser.mErr);
    }
    
    /**
     * Test various sunny-day operations of UIDL parser for single-line responses
     */
    public void testUIDLParserSingle() {      

        // single-line mode
        Pop3Store.Pop3Folder.UidlParser parser = mFolder.new UidlParser();

        // Test single-message OK response
        parser.parseSingleLine("+OK 101 " + UNIQUE_ID_1);
        assertEquals(101, parser.mMessageNumber);
        assertEquals(UNIQUE_ID_1, parser.mUniqueId);
        assertTrue(parser.mEndOfMessage);
        
        // Test single-message ERR response
        parser.parseSingleLine("-ERR what???");
        assertTrue(parser.mErr);
    }
    
    /**
     * Tests that variants on the RFC-specified formatting of UIDL work properly.
     */
    public void testUIDLComcastVariant() {
        
        // multi-line mode
        Pop3Store.Pop3Folder.UidlParser parser = mFolder.new UidlParser();
        
        // Comcast servers send multiple spaces in their darn UIDL strings.
        parser.parseMultiLine("101   " + UNIQUE_ID_1);
        assertEquals(101, parser.mMessageNumber);
        assertEquals(UNIQUE_ID_1, parser.mUniqueId);
        assertFalse(parser.mEndOfMessage);
        assertFalse(parser.mErr);
    }
    
    /**
     * Confirms simple non-SSL non-TLS login
     */
    public void testSimpleLogin() throws MessagingException {
        
        MockTransport mockTransport = openAndInjectMockTransport();
        
        // try to open it
        setupOpenFolder(mockTransport, 0, null);
        mFolder.open(OpenMode.READ_ONLY);
    }
    
    /**
     * TODO: Test with SSL negotiation (faked)
     * TODO: Test with SSL required but not supported
     * TODO: Test with TLS negotiation (faked)
     * TODO: Test with TLS required but not supported
     * TODO: Test calling getMessageCount(), getMessages(), etc.
     */
    
    /**
     * Test the operation of checkSettings(), which requires (a) a good open and (b) UIDL support.
     */
    public void testCheckSettings() throws MessagingException {

        MockTransport mockTransport = openAndInjectMockTransport();
        
        // scenario 1:  CAPA returns -ERR, so we try UIDL explicitly
        setupOpenFolder(mockTransport, 0, null);
        setupUidlSequence(mockTransport, 1);
        mockTransport.expect("QUIT", "");
        mStore.checkSettings();
        
        // scenario 2:  CAPA indicates UIDL, so we don't try UIDL
        setupOpenFolder(mockTransport, 0, "UIDL");
        mockTransport.expect("QUIT", "");
        mStore.checkSettings();
        
        // scenario 3:  CAPA returns -ERR, and UIDL fails
        try {
            setupOpenFolder(mockTransport, 0, null);
            mockTransport.expect("UIDL", "-ERR unsupported");
            mockTransport.expect("QUIT", "");
            mStore.checkSettings();
            fail("MessagingException was expected due to UIDL unsupported.");
        } catch (MessagingException me) {
            // this is expected, so eat it
        }
    }

    /**
     * Test small Store & Folder functions that manage folders & namespace
     */
    public void testStoreFoldersFunctions() throws MessagingException {
        
        // getPersonalNamespaces() always returns INBOX folder
        Folder[] folders = mStore.getPersonalNamespaces();
        assertEquals(1, folders.length);
        assertSame(mFolder, folders[0]);

        // getName() returns the name we were created with.  If "inbox", converts to INBOX
        assertEquals("INBOX", mFolder.getName());
        Pop3Store.Pop3Folder folderMixedCaseInbox = mStore.new Pop3Folder("iNbOx");
        assertEquals("INBOX", folderMixedCaseInbox.getName());
        Pop3Store.Pop3Folder folderNotInbox = mStore.new Pop3Folder("NOT-INBOX");
        assertEquals("NOT-INBOX", folderNotInbox.getName());
        
        // exists() true if name is INBOX
        assertTrue(mFolder.exists());
        assertTrue(folderMixedCaseInbox.exists());
        assertFalse(folderNotInbox.exists());
    }
        
    /**
     * Test small Folder functions that don't really do anything in Pop3
     */
    public void testSmallFolderFunctions() throws MessagingException {
            
        // getMode() returns OpenMode.READ_ONLY
        assertEquals(OpenMode.READ_ONLY, mFolder.getMode());
        
       // create() return false
        assertFalse(mFolder.create(FolderType.HOLDS_FOLDERS));
        assertFalse(mFolder.create(FolderType.HOLDS_MESSAGES));
        
        // getUnreadMessageCount() always returns -1
        assertEquals(-1, mFolder.getUnreadMessageCount());
        
        // getMessages(MessageRetrievalListener listener) is unsupported
        try {
            mFolder.getMessages(null);
            fail("Exception not thrown by getMessages()");
        } catch (UnsupportedOperationException e) {
            // expected - succeed
        }
        
        // getMessages(String[] uids, MessageRetrievalListener listener) is unsupported
        try {
            mFolder.getMessages(null, null);
            fail("Exception not thrown by getMessages()");
        } catch (UnsupportedOperationException e) {
            // expected - succeed
        }
        
        // getPermanentFlags() returns { Flag.DELETED }
        Flag[] flags = mFolder.getPermanentFlags();
        assertEquals(1, flags.length);
        assertEquals(Flag.DELETED, flags[0]);
        
        // appendMessages(Message[] messages) does nothing
        mFolder.appendMessages(null);
        
        // delete(boolean recurse) does nothing
        // TODO - it should!
        mFolder.delete(false);
        
        // expunge() returns null
        assertNull(mFolder.expunge());
        
        // copyMessages() is unsupported
        try {
            mFolder.copyMessages(null, null);
            fail("Exception not thrown by copyMessages()");
        } catch (UnsupportedOperationException e) {
            // expected - succeed
        }
    }
    
    /**
     * Test the process of opening and indexing a mailbox with one unread message in it.
     * 
     * TODO should create an instrumented listener to confirm all expected callbacks.  Then use
     * it everywhere we could have passed a message listener.
     */
    public void testOneUnread() throws MessagingException {
        
        MockTransport mockTransport = openAndInjectMockTransport();
        
        checkOneUnread(mockTransport);
    }

    /**
     * Test the scenario where the transport is "open" but not really (e.g. server closed).  Two
     * things should happen:  We should see an intermediate failure that makes sense, and the next
     * operation should reopen properly.
     * 
     * There are multiple versions of this test because we are simulating the steps of 
     * MessagingController.synchronizeMailboxSyncronous() and we will inject the failure a bit
     * further along in each case, to test various recovery points.
     * 
     * This test confirms that Pop3Store needs to call close() in the IOExceptionHandler in 
     * Pop3Folder.getMessages().
     */
    public void testCatchClosed1() throws MessagingException {
        
        MockTransport mockTransport = openAndInjectMockTransport();
        
        openFolderWithMessage(mockTransport);
        
        // cause the next sequence to fail on the readLine() calls
        mockTransport.closeInputStream();
        
        // index the message(s) - it should fail, because our stream is broken
        try {
            setupUidlSequence(mockTransport, 1);
            Message[] messages = mFolder.getMessages(1, 1, null);
            assertEquals(1, messages.length);
            assertEquals(getSingleMessageUID(1), messages[0].getUid());
            fail("Broken stream should cause getMessages() to throw.");
        }
        catch(MessagingException me) {
            // success
        }
        
        // At this point the UI would display connection error, which is fine.  Now, the real
        // test is, can we recover?  So I'll just repeat the above steps, without the failure.
        // NOTE: everything from here down is copied from testOneUnread() and should be consolidated
        
        // confirm that we're closed at this point
        assertFalse("folder should be 'closed' after an IOError", mFolder.isOpen());
        
        // and confirm that the next connection will be OK
        checkOneUnread(mockTransport);
    }

    /**
     * Test the scenario where the transport is "open" but not really (e.g. server closed).  Two
     * things should happen:  We should see an intermediate failure that makes sense, and the next
     * operation should reopen properly.
     * 
     * There are multiple versions of this test because we are simulating the steps of 
     * MessagingController.synchronizeMailboxSyncronous() and we will inject the failure a bit
     * further along in each case, to test various recovery points.
     * 
     * This test confirms that Pop3Store needs to call close() in the first IOExceptionHandler in 
     * Pop3Folder.fetch(), for a failure in the call to indexUids().
     */
    public void testCatchClosed2() throws MessagingException {
        
        MockTransport mockTransport = openAndInjectMockTransport();
        
        openFolderWithMessage(mockTransport);
        
        // index the message(s)
        setupUidlSequence(mockTransport, 1);
        Message[] messages = mFolder.getMessages(1, 1, null);
        assertEquals(1, messages.length);
        assertEquals(getSingleMessageUID(1), messages[0].getUid());         
        
        // cause the next sequence to fail on the readLine() calls
        mockTransport.closeInputStream();
        
        try {
            // try the basic fetch of flags & envelope
            setupListSequence(mockTransport, 1);
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.FLAGS);
            fp.add(FetchProfile.Item.ENVELOPE);
            mFolder.fetch(messages, fp, null);
            assertEquals(PER_MESSAGE_SIZE, messages[0].getSize());
            fail("Broken stream should cause fetch() to throw.");
        }
        catch(MessagingException me) {
            // success
        }

        // At this point the UI would display connection error, which is fine.  Now, the real
        // test is, can we recover?  So I'll just repeat the above steps, without the failure.
        // NOTE: everything from here down is copied from testOneUnread() and should be consolidated
        
        // confirm that we're closed at this point
        assertFalse("folder should be 'closed' after an IOError", mFolder.isOpen());
        
        // and confirm that the next connection will be OK
        checkOneUnread(mockTransport);
    }
    
    /**
     * Test the scenario where the transport is "open" but not really (e.g. server closed).  Two
     * things should happen:  We should see an intermediate failure that makes sense, and the next
     * operation should reopen properly.
     * 
     * There are multiple versions of this test because we have to check additional places where
     * Pop3Store and/or Pop3Folder should be dealing with IOErrors.
     * 
     * This test confirms that Pop3Store needs to call close() in the first IOExceptionHandler in 
     * Pop3Folder.fetch(), for a failure in the call to fetchEnvelope().
     */
    public void testCatchClosed2a() {
        // TODO cannot write this test until we can inject stream closures mid-sequence
    }
        
    /**
     * Test the scenario where the transport is "open" but not really (e.g. server closed).  Two
     * things should happen:  We should see an intermediate failure that makes sense, and the next
     * operation should reopen properly.
     * 
     * There are multiple versions of this test because we are simulating the steps of 
     * MessagingController.synchronizeMailboxSyncronous() and we will inject the failure a bit
     * further along in each case, to test various recovery points.
     * 
     * This test confirms that Pop3Store needs to call close() in the second IOExceptionHandler in 
     * Pop3Folder.fetch().
     */
    public void testCatchClosed3() throws MessagingException {
        
        MockTransport mockTransport = openAndInjectMockTransport();
        
        openFolderWithMessage(mockTransport);
        
        // index the message(s)
        setupUidlSequence(mockTransport, 1);
        Message[] messages = mFolder.getMessages(1, 1, null);
        assertEquals(1, messages.length);
        assertEquals(getSingleMessageUID(1), messages[0].getUid());         

        // try the basic fetch of flags & envelope
        setupListSequence(mockTransport, 1);
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.FLAGS);
        fp.add(FetchProfile.Item.ENVELOPE);
        mFolder.fetch(messages, fp, null);
        assertEquals(PER_MESSAGE_SIZE, messages[0].getSize());

        // cause the next sequence to fail on the readLine() calls
        mockTransport.closeInputStream();

        try {
            // now try fetching the message
            setupSingleMessage(mockTransport, 1, false);
            fp = new FetchProfile();
            fp.add(FetchProfile.Item.BODY);
            mFolder.fetch(messages, fp, null);
            checkFetchedMessage(messages[0], 1, false);
            fail("Broken stream should cause fetch() to throw.");
        }
        catch(MessagingException me) {
            // success
        }

        // At this point the UI would display connection error, which is fine.  Now, the real
        // test is, can we recover?  So I'll just repeat the above steps, without the failure.
        // NOTE: everything from here down is copied from testOneUnread() and should be consolidated
        
        // confirm that we're closed at this point
        assertFalse("folder should be 'closed' after an IOError", mFolder.isOpen());
        
        // and confirm that the next connection will be OK
        checkOneUnread(mockTransport);
    }
    
    /**
     * Test the scenario where the transport is "open" but not really (e.g. server closed).  Two
     * things should happen:  We should see an intermediate failure that makes sense, and the next
     * operation should reopen properly.
     * 
     * There are multiple versions of this test because we have to check additional places where
     * Pop3Store and/or Pop3Folder should be dealing with IOErrors.
     * 
     * This test confirms that Pop3Store needs to call close() in the IOExceptionHandler in 
     * Pop3Folder.setFlags().
     */
    public void testCatchClosed4() throws MessagingException {
        
        MockTransport mockTransport = openAndInjectMockTransport();
        
        openFolderWithMessage(mockTransport);
        
        // index the message(s)
        setupUidlSequence(mockTransport, 1);
        Message[] messages = mFolder.getMessages(1, 1, null);
        assertEquals(1, messages.length);
        assertEquals(getSingleMessageUID(1), messages[0].getUid());
        
        // cause the next sequence to fail on the readLine() calls
        mockTransport.closeInputStream();

        // delete 'em all - should fail because of broken stream
        try {
            mockTransport.expect("DELE 1", "+OK message deleted");
            mFolder.setFlags(messages, new Flag[] { Flag.DELETED }, true);
            fail("Broken stream should cause fetch() to throw.");
        }
        catch(MessagingException me) {
            // success
        }

        // At this point the UI would display connection error, which is fine.  Now, the real
        // test is, can we recover?  So I'll just repeat the above steps, without the failure.
        // NOTE: everything from here down is copied from testOneUnread() and should be consolidated
        
        // confirm that we're closed at this point
        assertFalse("folder should be 'closed' after an IOError", mFolder.isOpen());
        
        // and confirm that the next connection will be OK
        checkOneUnread(mockTransport);
    }
        
    /**
     * Test the scenario where the transport is "open" but not really (e.g. server closed).  Two
     * things should happen:  We should see an intermediate failure that makes sense, and the next
     * operation should reopen properly.
     * 
     * There are multiple versions of this test because we have to check additional places where
     * Pop3Store and/or Pop3Folder should be dealing with IOErrors.
     * 
     * This test confirms that Pop3Store needs to call close() in the first IOExceptionHandler in 
     * Pop3Folder.open().
     */
    public void testCatchClosed5() {
        // TODO cannot write this test until we can inject stream closures mid-sequence
    }
        
    /**
     * Test the scenario where the transport is "open" but not really (e.g. server closed).  Two
     * things should happen:  We should see an intermediate failure that makes sense, and the next
     * operation should reopen properly.
     * 
     * There are multiple versions of this test because we have to check additional places where
     * Pop3Store and/or Pop3Folder should be dealing with IOErrors.
     * 
     * This test confirms that Pop3Store needs to call close() in the second IOExceptionHandler in 
     * Pop3Folder.open() (when it calls STAT).
     */
    public void testCatchClosed6() {
        // TODO cannot write this test until we can inject stream closures mid-sequence
    }
        
    /**
     * Given an initialized mock transport, open it and attempt to "read" one unread message from 
     * it.  This can be used as a basic test of functionality and it should be possible to call this
     * repeatedly (if you close the folder between calls).
     * 
     * @param mockTransport the mock transport we're using
     */
    private void checkOneUnread(MockTransport mockTransport) throws MessagingException {
        openFolderWithMessage(mockTransport);
        
        // index the message(s)
        setupUidlSequence(mockTransport, 1);
        Message[] messages = mFolder.getMessages(1, 1, null);
        assertEquals(1, messages.length);
        assertEquals(getSingleMessageUID(1), messages[0].getUid());
        
        // try the basic fetch of flags & envelope
        setupListSequence(mockTransport, 1);
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.FLAGS);
        fp.add(FetchProfile.Item.ENVELOPE);
        mFolder.fetch(messages, fp, null);
        assertEquals(PER_MESSAGE_SIZE, messages[0].getSize());
        
        // A side effect of how messages work is that if you get fields that are empty, 
        // then empty arrays are written back into the parsed header fields (e.g. mTo, mFrom).  The
        // standard message parser needs to clear these before parsing.  Make sure that this
        // is happening.  (This doesn't affect IMAP, which reads the headers directly via
        // IMAP evelopes.)
        MimeMessage message = (MimeMessage) messages[0];
        message.getRecipients(RecipientType.TO);
        message.getRecipients(RecipientType.CC);
        message.getRecipients(RecipientType.BCC);

        // now try fetching the message
        setupSingleMessage(mockTransport, 1, false);
        fp = new FetchProfile();
        fp.add(FetchProfile.Item.BODY);
        mFolder.fetch(messages, fp, null);
        checkFetchedMessage(messages[0], 1, false);
    }
    
    /**
     * Set up a basic MockTransport. open it, and inject it into mStore
     */
    private MockTransport openAndInjectMockTransport() {
        // Create mock transport and inject it into the POP3Store that's already set up
        MockTransport mockTransport = new MockTransport();
        mockTransport.setSecurity(Transport.CONNECTION_SECURITY_NONE);
        mStore.setTransport(mockTransport);
        return mockTransport;
    }
    
    /**
     * Open a folder that's preloaded with one unread message.
     * 
     * @param mockTransport the mock transport we're using
     */
    private void openFolderWithMessage(MockTransport mockTransport) throws MessagingException {
        // try to open it
        setupOpenFolder(mockTransport, 1, null);
        mFolder.open(OpenMode.READ_ONLY);
        
        // check message count
        assertEquals(1, mFolder.getMessageCount());
    }
    
    /**
     * Look at a fetched message and confirm that it is complete.
     * 
     * TODO this needs to be more dynamic, not just hardcoded for empty message #1.
     * 
     * @param message the fetched message to be checked
     * @param msgNum the message number
     */
    private void checkFetchedMessage(Message message, int msgNum, boolean body) 
            throws MessagingException {
        // check To:
        Address[] to = message.getRecipients(RecipientType.TO);
        assertNotNull(to);
        assertEquals(1, to.length);
        assertEquals("Smith@Registry.Org", to[0].getAddress());
        assertNull(to[0].getPersonal());
        
        // check From:
        Address[] from = message.getFrom();
        assertNotNull(from);
        assertEquals(1, from.length);
        assertEquals("Jones@Registry.Org", from[0].getAddress());
        assertNull(from[0].getPersonal());
        
        // check Cc:
        Address[] cc = message.getRecipients(RecipientType.CC);
        assertNotNull(cc);
        assertEquals(1, cc.length);
        assertEquals("Chris@Registry.Org", cc[0].getAddress());
        assertNull(cc[0].getPersonal());

        // check Reply-To:
        Address[] replyto = message.getReplyTo();
        assertNotNull(replyto);
        assertEquals(1, replyto.length);
        assertEquals("Roger@Registry.Org", replyto[0].getAddress());
        assertNull(replyto[0].getPersonal());

        // TODO date
        
        // TODO check body (if applicable)
    }
    
    /**
     * Helper which stuffs the mock with enough strings to satisfy a call to Pop3Folder.open()
     * 
     * @param mockTransport the mock transport we're using
     * @param statCount the number of messages to indicate in the STAT
     * @param capabilities if non-null, comma-separated list of capabilities
     */
    private void setupOpenFolder(MockTransport mockTransport, int statCount, String capabilities) {
        mockTransport.expect(null, "+OK Hello there from the Mock Transport.");
        if (capabilities == null) {
            mockTransport.expect("CAPA", "-ERR unimplemented");
        } else {
            mockTransport.expect("CAPA", "+OK capabilities follow");
            mockTransport.expect(null, capabilities.split(","));        // one capability per line
            mockTransport.expect(null, ".");                            // terminated by "."
        }
        mockTransport.expect("USER user", "+OK User name accepted");
        mockTransport.expect("PASS password", "+OK Logged in");
        String stat = "+OK " + Integer.toString(statCount) + " " + 
                Integer.toString(PER_MESSAGE_SIZE * statCount);
        mockTransport.expect("STAT", stat);
    }
    
    /**
     * Setup expects for a UIDL on a mailbox with 0 or more messages in it.
     * @param transport The mock transport to preload
     * @param numMessages The number of messages to return from UIDL.
     */
    private static void setupUidlSequence(MockTransport transport, int numMessages) {
        transport.expect("UIDL", "+OK sending UIDL list");          
        for (int msgNum = 1; msgNum <= numMessages; ++msgNum) {
            transport.expect(null, Integer.toString(msgNum) + " " + getSingleMessageUID(msgNum));
        }
        transport.expect(null, ".");
    }
    
    /**
     * Setup expects for a LIST on a mailbox with 0 or more messages in it.
     * @param transport The mock transport to preload
     * @param numMessages The number of messages to return from LIST.
     */
    private static void setupListSequence(MockTransport transport, int numMessages) {
        transport.expect("LIST", "+OK sending scan listing");          
        for (int msgNum = 1; msgNum <= numMessages; ++msgNum) {
            transport.expect(null, Integer.toString(msgNum) + " " + 
                    Integer.toString(PER_MESSAGE_SIZE * msgNum));
        }
        transport.expect(null, ".");
    }
    
    /**
     * Setup a single message to be retrieved.
     * 
     * Per RFC822 here is a minimal message header:
     *     Date:     26 Aug 76 1429 EDT
     *     From:     Jones@Registry.Org
     *     To:       Smith@Registry.Org
     * 
     * We'll add the following fields to support additional tests:
     *     Cc:       Chris@Registry.Org
     *     Reply-To: Roger@Registry.Org
     *     
     * @param transport the mock transport to preload
     * @param msgNum the message number to expect and return
     * @param body if true, a non-empty body will be added
     */
    private static void setupSingleMessage(MockTransport transport, int msgNum, boolean body) {
        transport.expect("RETR " + Integer.toString(msgNum), "+OK message follows");
        transport.expect(null, "Date: 26 Aug 76 1429 EDT");
        transport.expect(null, "From: Jones@Registry.Org");
        transport.expect(null, "To:   Smith@Registry.Org");
        transport.expect(null, "CC:   Chris@Registry.Org");
        transport.expect(null, "Reply-To: Roger@Registry.Org");
        transport.expect(null, "");
        transport.expect(null, ".");
    }

    /**
     * Generates a simple unique code for each message.  Repeatable.
     * @param msgNum The message number
     * @return a string that can be used as the UID
     */
    private static String getSingleMessageUID(int msgNum) {
        final String UID_HEAD = "ABCDEF-";
        final String UID_TAIL = "";
        return UID_HEAD + Integer.toString(msgNum) + UID_TAIL;
    }
}
