/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package java.util.logging;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.harmony.logging.internal.nls.Messages;

/**
 * A {@code StreamHandler} object writes log messages to an output stream, that
 * is, objects of the class {@link java.io.OutputStream}.
 * <p>
 * A {@code StreamHandler} object reads the following properties from the log
 * manager to initialize itself:
 * <ul>
 * <li>java.util.logging.StreamHandler.level specifies the logging level,
 * defaults to {@code Level.INFO} if this property is not found or has an
 * invalid value.
 * <li>java.util.logging.StreamHandler.filter specifies the name of the filter
 * class to be associated with this handler, defaults to {@code null} if this
 * property is not found or has an invalid value.
 * <li>java.util.logging.StreamHandler.formatter specifies the name of the
 * formatter class to be associated with this handler, defaults to
 * {@code java.util.logging.SimpleFormatter} if this property is not found or
 * has an invalid value.
 * <li>java.util.logging.StreamHandler.encoding specifies the encoding this
 * handler will use to encode log messages, defaults to {@code null} if this
 * property is not found or has an invalid value.
 * </ul>
 * </p>
 * <p>
 * This class is not thread-safe.
 * </p>
 * 
 * @since Android 1.0
 */
public class StreamHandler extends Handler {
    // the output stream this handler writes to
    private OutputStream os;

    // the writer that writes to the output stream
    private Writer writer;

    // the flag indicating whether the writer has been initialized
    private boolean writerNotInitialized;

    /**
     * Constructs a {@code StreamHandler} object. The new stream handler
     * does not have an associated output stream.
     * 
     * @since Android 1.0
     */
    public StreamHandler() {
        initProperties("INFO", null, "java.util.logging.SimpleFormatter",  //$NON-NLS-1$//$NON-NLS-2$
                null);
        this.os = null;
        this.writer = null;
        this.writerNotInitialized = true;
    }

    /**
     * Constructs a {@code StreamHandler} object with the supplied output
     * stream. Default properties are read.
     * 
     * @param os
     *            the output stream this handler writes to.
     */
    StreamHandler(OutputStream os) {
        this();
        this.os = os;
    }

    /**
     * Constructs a {@code StreamHandler} object. The specified default values
     * will be used if the corresponding properties are not found in the log
     * manager's properties.
     */
    StreamHandler(String defaultLevel, String defaultFilter,
            String defaultFormatter, String defaultEncoding) {
        initProperties(defaultLevel, defaultFilter, defaultFormatter,
                defaultEncoding);
        this.os = null;
        this.writer = null;
        this.writerNotInitialized = true;
    }

    /**
     * Constructs a {@code StreamHandler} object with the supplied output stream
     * and formatter.
     * 
     * @param os
     *            the output stream this handler writes to.
     * @param formatter
     *            the formatter this handler uses to format the output.
     * @throws NullPointerException
     *             if {@code os} or {@code formatter} is {@code null}.
     * @since Android 1.0
     */
    public StreamHandler(OutputStream os, Formatter formatter) {
        this();
        if (os == null) {
            // logging.2=The OutputStream parameter is null
            throw new NullPointerException(Messages.getString("logging.2")); //$NON-NLS-1$
        }
        if (formatter == null) {
            // logging.3=The Formatter parameter is null.
            throw new NullPointerException(Messages.getString("logging.3")); //$NON-NLS-1$
        }
        this.os = os;
        internalSetFormatter(formatter);
    }

    // initialize the writer
    private void initializeWritter() {
        this.writerNotInitialized = false;
        if (null == getEncoding()) {
            this.writer = new OutputStreamWriter(this.os);
        } else {
            try {
                this.writer = new OutputStreamWriter(this.os, getEncoding());
            } catch (UnsupportedEncodingException e) {
                /*
                 * Should not happen because it's checked in
                 * super.initProperties().
                 */
            }
        }
        write(getFormatter().getHead(this));
    }

    // Write a string to the output stream.
    private void write(String s) {
        try {
            this.writer.write(s);
        } catch (Exception e) {
            // logging.14=Exception occurred when writing to the output stream.
            getErrorManager().error(Messages.getString("logging.14"), e, //$NON-NLS-1$
                    ErrorManager.WRITE_FAILURE);
        }
    }

    /**
     * Sets the output stream this handler writes to. Note it does nothing else.
     * 
     * @param newOs
     *            the new output stream
     */
    void internalSetOutputStream(OutputStream newOs) {
        this.os = newOs;
    }

    
    /**
     * Sets the output stream this handler writes to. If there's an existing
     * output stream, the tail string of the associated formatter will be
     * written to it. Then it will be flushed, closed and replaced with 
     * {@code os}.
     * 
     * @param os
     *            the new output stream.
     * @throws SecurityException
     *             if a security manager determines that the caller does not
     *             have the required permission.
     * @throws NullPointerException
     *             if {@code os} is {@code null}.
     */
    protected void setOutputStream(OutputStream os) {
        if (null == os) {
            throw new NullPointerException();
        }
        LogManager.getLogManager().checkAccess();
        close(true);
        this.writer = null;
        this.os = os;
        this.writerNotInitialized = true;
    }

    /**
     * Sets the character encoding used by this handler. A {@code null} value
     * indicates that the default encoding should be used.
     * 
     * @param encoding
     *            the character encoding to set.
     * @throws SecurityException
     *             if a security manager determines that the caller does not
     *             have the required permission.
     * @throws UnsupportedEncodingException
     *             if the specified encoding is not supported by the runtime.
     * @since Android 1.0
     */
    @Override
    public void setEncoding(String encoding) throws SecurityException,
            UnsupportedEncodingException {
        //flush first before set new encoding
        this.flush();
        super.setEncoding(encoding);
        // renew writer only if the writer exists
        if (null != this.writer) {
            if (null == getEncoding()) {
                this.writer = new OutputStreamWriter(this.os);
            } else {
                try {
                    this.writer = new OutputStreamWriter(this.os, getEncoding());
                } catch (UnsupportedEncodingException e) {
                    /*
                     * Should not happen because it's checked in
                     * super.initProperties().
                     */
                    throw new AssertionError(e);
                }
            }
        }
    }

    /**
     * Closes this handler, but the underlying output stream is only closed if
     * {@code closeStream} is {@code true}. Security is not checked.
     * 
     * @param closeStream
     *            whether to close the underlying output stream.
     */
    void close(boolean closeStream) {
        if (null != this.os) {
            if (this.writerNotInitialized) {
                initializeWritter();
            }
            write(getFormatter().getTail(this));
            try {
                this.writer.flush();
                if (closeStream) {
                    this.writer.close();
                    this.writer = null;
                    this.os = null;
                }
            } catch (Exception e) {
                // logging.15=Exception occurred when closing the output stream.
                getErrorManager().error(Messages.getString("logging.15"), e, //$NON-NLS-1$
                        ErrorManager.CLOSE_FAILURE);
            }
        }
    }

    /**
     * Closes this handler. The tail string of the formatter associated with
     * this handler is written out. A flush operation and a subsequent close
     * operation is then performed upon the output stream. Client applications
     * should not use a handler after closing it.
     * 
     * @throws SecurityException
     *             if a security manager determines that the caller does not
     *             have the required permission.
     * @since Android 1.0
     */
    @Override
    public void close() {
        LogManager.getLogManager().checkAccess();
        close(true);
    }

    /**
     * Flushes any buffered output.
     * 
     * @since Android 1.0
     */
    @Override
    public void flush() {
        if (null != this.os) {
            try {
                if (null != this.writer) {
                    this.writer.flush();
                } else {
                    this.os.flush();
                }
            } catch (Exception e) {
                // logging.16=Exception occurred while flushing the output stream.
                getErrorManager().error(Messages.getString("logging.16"), //$NON-NLS-1$
                        e, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    /**
     * Accepts a logging request. The log record is formatted and written to the
     * output stream if the following three conditions are met:
     * <ul>
     * <li>the supplied log record has at least the required logging level;
     * <li>the supplied log record passes the filter associated with this
     * handler, if any;
     * <li>the output stream associated with this handler is not {@code null}.
     * </ul>
     * If it is the first time a log record is written out, the head string of
     * the formatter associated with this handler is written out first.
     * 
     * @param record
     *            the log record to be logged.
     * @since Android 1.0
     */
    @Override
    public synchronized void publish(LogRecord record) {
        try {
            if (this.isLoggable(record)) {
                if (this.writerNotInitialized) {
                    initializeWritter();
                }
                String msg = null;
                try {
                    msg = getFormatter().format(record);
                } catch (Exception e) {
                    // logging.17=Exception occurred while formatting the log record.
                    getErrorManager().error(Messages.getString("logging.17"), //$NON-NLS-1$
                            e, ErrorManager.FORMAT_FAILURE);
                }
                write(msg);
            }
        } catch (Exception e) {
            // logging.18=Exception occurred while logging the record.
            getErrorManager().error(Messages.getString("logging.18"), e, //$NON-NLS-1$
                    ErrorManager.GENERIC_FAILURE);
        }
    }

    /**
     * Determines whether the supplied log record needs to be logged. The
     * logging levels are checked as well as the filter. The output stream of
     * this handler is also checked. If it is {@code null}, this method returns
     * {@code false}.
     * <p>
     * Notice : Case of no output stream will return {@code false}.
     * </p>
     * 
     * @param record
     *            the log record to be checked.
     * @return {@code true} if {@code record} needs to be logged, {@code false}
     *         otherwise.
     * @since Android 1.0
     */
    @Override
    public boolean isLoggable(LogRecord record) {
        if (null == record) {
            return false;
        }
        if (null != this.os && super.isLoggable(record)) {
            return true;
        }
        return false;
    }

}
