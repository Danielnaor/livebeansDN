/*
 * The MIT License
 *
 * Copyright 2016 Luke Dawkes.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package livebeansclient;

import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import livebeansclient.gui.TabListener;
import livebeansclient.gui.TabListenerHandler;
import livebeansclient.threads.ClientHeartbeat;
import livebeansclient.threads.CodeSegmentSynchroniser;
import livebeanscommon.ILiveBeansClient;
import livebeanscommon.ILiveBeansCodeSegment;
import livebeanscommon.ILiveBeansServer;
import org.openide.util.Exceptions;

/**
 *
 * @author Luke Dawkes
 */
public class LiveBeansClient extends UnicastRemoteObject implements Serializable, ILiveBeansClient {

    private static LiveBeansClient _instance;

    public static LiveBeansClient getInstance() {
        if (_instance == null) {
            try {
                _instance = new LiveBeansClient();
            } catch (RemoteException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return _instance;
    }

    private int _clientID;
    private String _clientName;
    private ILiveBeansServer _currentServer;
    private final String _ipAddressRegex;
    private final Pattern _ipAddressRegexPattern;
    private TabListenerHandler _tabListenerHandler;
    private TabListener _tabListener;

    private final ScheduledExecutorService _scheduler;

    private final List<ILiveBeansCodeSegment> _segmentBacklog;

    private LiveBeansClient() throws RemoteException {
        _ipAddressRegex = "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
        _ipAddressRegexPattern = Pattern.compile(_ipAddressRegex);

        _scheduler = Executors.newScheduledThreadPool(2);
        _segmentBacklog = new ArrayList<>();
    }

    /* Code Segment Methods */
    /**
     * Adds an addition segment to the backlog
     *
     * @param documentName The name of the document the code is in
     * @param projectName The name of the project the document is in
     * @param code The code to be updated
     * @param codeOffset The offset of the updated code within the document
     * @throws RemoteException
     */
    public void addSegmentToBacklog(String documentName,
            String projectName,
            String code,
            int codeOffset) throws RemoteException {
        CodeSegment codeSegment = new CodeSegment();
        codeSegment.setAuthorID(_clientID);
        codeSegment.setDocumentName(documentName);
        codeSegment.setProjectName(projectName);
        codeSegment.setCodeText(code);
        codeSegment.setDocumentOffset(codeOffset);

        _segmentBacklog.add(codeSegment);
    }

    /**
     * Adds a removal segment to the backlog
     *
     * @param documentName The name of the document the code is in
     * @param projectName The name of the project the document is in
     * @param codeOffset The offset of the updated code within the document
     * @param codeLength The length of the updated code
     * @throws RemoteException
     */
    public void addSegmentToBacklog(String documentName, String projectName, int codeOffset, int codeLength) throws RemoteException {
        CodeSegment codeSegment = new CodeSegment();
        codeSegment.setAuthorID(_clientID);
        codeSegment.setDocumentName(documentName);
        codeSegment.setProjectName(projectName);
        codeSegment.setDocumentOffset(codeOffset);
        codeSegment.setCodeLength(codeLength);

        _segmentBacklog.add(codeSegment);
    }

    /**
     * Adds an addition segment to the backlog
     *
     * @param documentName The name of the document the code is in
     * @param code The code to be updated
     * @param codeOffset The offset of the code within the document
     * @throws RemoteException
     */
    public void addSegmentToBacklog(String documentName, String code, int codeOffset) throws RemoteException {
        CodeSegment codeSegment = new CodeSegment();
        codeSegment.setAuthorID(_clientID);
        codeSegment.setDocumentName(documentName);
        codeSegment.setCodeText(code);
        codeSegment.setDocumentOffset(codeOffset);

        _segmentBacklog.add(codeSegment);
    }

    /**
     * Adds a removal segment to the backlog
     *
     * @param documentName The name of the document the code is in
     * @param codeOffset The offset of the code within the document
     * @param codeLength The length of the updated code
     * @throws RemoteException
     */
    public void addSegmentToBacklog(String documentName, int codeOffset, int codeLength) throws RemoteException {
        CodeSegment codeSegment = new CodeSegment();
        codeSegment.setDocumentName(documentName);
        codeSegment.setAuthorID(_clientID);
        codeSegment.setDocumentOffset(codeOffset);
        codeSegment.setCodeLength(codeLength);

        _segmentBacklog.add(codeSegment);
    }

    @Override
    public void setID(int newID) throws RemoteException {
        _clientID = newID;
    }

    @Override
    public void setName(String newName) throws RemoteException {
        _clientName = newName;
    }

    @Override
    public void connectToServer(String serverAddress) throws RemoteException {
        Matcher regexMatcher = _ipAddressRegexPattern.matcher(serverAddress);

        if (regexMatcher.matches()) {
            System.out.println(String.format("[CLIENT-INFO] IP Address (%s) matches regex pattern", serverAddress));
        } else {
            displayDialog("Incorrect IP Format", "You must enter a valid IP (e.g. 192.168.0.1)", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Registry reg = LocateRegistry.getRegistry(serverAddress);

            _currentServer = (ILiveBeansServer) reg.lookup("LiveBeansServer");
            _currentServer.registerClient(this);

            _scheduler.scheduleAtFixedRate(new ClientHeartbeat(), 2, 2, TimeUnit.SECONDS);
            _scheduler.scheduleAtFixedRate(new CodeSegmentSynchroniser(), 1, 1, TimeUnit.SECONDS);

            _tabListenerHandler = TabListenerHandler.getInstance();
            _tabListener = TabListener.getInstance();

            System.out.println("[CLIENT-INFO] Found Server.");
        } catch (NotBoundException ex) {
            System.out.println(ex.getMessage());
            return;
        }

        System.out.println(String.format("[CLIENT-INFO] Current server is %s", _currentServer == null ? "null" : "not null"));
    }

    public void postConnect() {
        _tabListenerHandler.setUpListeners();
    }

    @Override
    public void disconnectFromServer() {
        try {
            _currentServer.unRegisterClient(this);
        } catch (RemoteException ex) {
            System.out.println(ex.getMessage());
        } finally {
            _scheduler.shutdown();
            _currentServer = null;
        }
    }

    @Override
    public int getID() throws RemoteException {
        return _clientID;
    }

    @Override
    public String getName() throws RemoteException {
        return _clientName;
    }

    @Override
    public ILiveBeansServer getServer() throws RemoteException {
        return _currentServer;
    }

    public boolean isConnected() {
        return _currentServer != null;
    }

    @Override
    public void updateLocalCode(List<ILiveBeansCodeSegment> codeSegments) throws RemoteException {
        System.out.println(String.format("[CLIENT-LOG] Received collection of %d code segments:", codeSegments.size()));

        for (ILiveBeansCodeSegment codeSegment : codeSegments) {
            String documentName = codeSegment.getDocumentName();
            StyledDocument document = _tabListenerHandler.getOpenDocument(documentName);

            if (document != null) {
                String code = codeSegment.getCodeText();

                if (code == null || code.equals("")) {
                    // Because no code has been sent across, assume that
                    // the server wants a segment removed from all clients
                    try {
                        _tabListener.setPaused(true);
                        document.remove(codeSegment.getDocumentOffset(),
                                codeSegment.getCodeLength());

                        System.out.println("[CLIENT-INFO] Removed code from document");
                    } catch (BadLocationException ex) {
                        System.out.println("[CLIENT-WARNING] Failed to remove code from document\r\n" + ex);
                    } finally {
                        _tabListener.setPaused(false);
                    }
                } else {

                    // Code is contained within the code segment, so
                    // use the variables to add code to local document
                    try {
                        _tabListener.setPaused(true);
                        Integer offset = codeSegment.getDocumentOffset();
                        document.insertString(offset,
                                code,
                                document.getLogicalStyle(offset));
                        System.out.println("[CLIENT-INFO] Added code to document");
                    } catch (BadLocationException ex) {
                        System.out.println("[CLIENT-WARNING] Failed to add code to document\r\n" + ex);
                    } finally {
                        _tabListener.setPaused(false);
                    }
                }
            } else {
                // Edit local file
            }

            _tabListenerHandler.saveDocument(documentName);
            System.out.println(String.format("\t[CLIENT-LOG] Code segment contains: %s", codeSegment.getCodeText()));
        }
    }

    @Override
    public void updateRemoteCode() {
        if (_segmentBacklog.isEmpty()) {
            return;
        }

        System.out.println("[CLIENT-INFO] Synchronising...");

        synchronized (_segmentBacklog) {
            try {
                _currentServer.distributeCodeSegments(_segmentBacklog, _clientID);

                System.out.println(String.format("[CLIENT-INFO] Synchronised %d code segment(s)", _segmentBacklog.size()));

                _segmentBacklog.clear();

            } catch (RemoteException ex) {
                System.out.println("[CLIENT-WARNING] There was an error synchronising the code segments\r\n" + ex);
            }
        }

    }

    public void displayDialog(String title, String message, int messageType) {
        JOptionPane.showMessageDialog(new JFrame(), message, title, messageType);
    }
}
