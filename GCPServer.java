import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.io.DataOutputStream;
import java.util.TimerTask;

public class GCPServer {
    private ServerSocket mFBoxServerSocket;
    private ArrayList<FBoxSession> mFBoxSessions = null;
    private ArrayList<FBox_Sessions> mAvailableFBoxSessionKeys = null;
    private Timer mFBoxServerReceiveTimer = null;
    private Timer mFBoxServerAcceptTimer = null;

    //personal cloud

    private ServerSocket mFBoxPersonalCloudSocket;
    private ArrayList<FBoxPersonalCloudSession> mFBoxPersonalCloudSessions = null;
    private ArrayList<FBox_Personal> mAvailablePersonalCloudKeys = null;
    private Timer mFBoxPersonalCloudReceiveTimer = null;
    private Timer mFBoxPersonalCloudAcceptTimer = null;

    private enum FBox_Personal {
        kFBoxSession_1(3000),
        kFBoxSession_2(3100),
        kFBoxSession_3(3200),
        kFBoxSession_4(3300),
        kFBoxSession_5(3400),        // As of now we are limiting a maximum of 10 users to FBox
        kFBoxSession_6(3500),
        kFBoxSession_7(3600),
        kFBoxSession_8(3700),
        kFBoxSession_9(3800),
        kFBoxSession_10(3900);

        public int val;

        private FBox_Personal(int value) {
            this.val = value;
        }
    }

    private void checkAliveFBoxSession() {

        for(int i = 0; i < mFBoxSessions.size();i++) {
            try {
                FBoxSession check = mFBoxSessions.get(i);
                System.out.println("closing board fault......................"+check.getKeepAvailable());
                if(check.getKeepAvailable() > 1){
                    System.out.println("closing board fault1");
                    // sending again to check..
                    String response = "CLOSE_KEEP_ALIVE";
                    BufferedOutputStream outStream = null;
                    String sendData = hideMsg(response);
                    try {
                        outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                        outStream.write(sendData.getBytes(), 0, sendData.length());
                        outStream.flush();
                        Thread.sleep(2000);
                        System.out.println("confirm the data is alive or not");
                        if(check.getKeepAvailable() < 1) {
                            check.setKeepAvailable(check.getKeepAvailable()+1);
                        } else {
                            System.out.println("closing board request");
                            sendConnectionClose(i);
                            i = 0;
                        }
                    } catch (Exception e) {
                        System.out.println("exception error closing socket...");
                        sendConnectionClose(i);
                        i = 0;
                    }
                } else {
                    check.setKeepAvailable(check.getKeepAvailable()+1);
                }
            } catch (Exception e) {
                System.out.println("exception connect socket now closing......");
                sendConnectionClose(i);
                i = 0;
            }
        }

        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        checkAliveFBoxSession();
                    }
                },
                12000
        );

    }

    private void sendConnectionClose(int i) {
        try {
            FBoxSession check = mFBoxSessions.get(i);
            String FBoxId = check.getFBox_ID();
            boolean sendAll = check.getBoardSession();
            for(int j = 0; j < mFBoxSessions.size(); j++) {
                if(i == j) {
                    continue;
                }
                try {
                    FBoxSession recheck = mFBoxSessions.get(j);
                    if(FBoxId.equalsIgnoreCase(recheck.getFBox_ID())) {
                        if (sendAll == true) {
                            System.out.println("closing board fault");
                            String response = "Close_board_fault";
                            BufferedOutputStream outStream = null;
                            String sendData = hideMsg(response);
                            try {
                                outStream = new BufferedOutputStream(recheck.getSocket().getOutputStream());
                                outStream.write(sendData.getBytes(), 0, sendData.length());
                                outStream.flush();
                            } catch(Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            if (recheck.getBoardSession() == true) {
                                System.out.println("closing mobile fault");
                                String response = "Close_mobile_fault";
                                BufferedOutputStream outStream = null;
                                String sendData = hideMsg(response);
                                try {
                                    outStream = new BufferedOutputStream(recheck.getSocket().getOutputStream());
                                    outStream.write(sendData.getBytes(), 0, sendData.length());
                                    outStream.flush();
                                } catch(Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    FBoxSession recheck = mFBoxSessions.get(i);
                    mAvailableFBoxSessionKeys.add(recheck.getKey());
                    mFBoxSessions.remove(i);
                    e.printStackTrace();
                }
            }
            if(sendAll == true) {
                for(int j = 0; j < mFBoxSessions.size(); j++) {
                    FBoxSession recheck = mFBoxSessions.get(j);
                    if (FBoxId.equalsIgnoreCase(recheck.getFBox_ID())) {
                        System.out.println("remove... board fault");
                        mAvailableFBoxSessionKeys.add(recheck.getKey());
                        mFBoxSessions.remove(j);
                    }
                }
            } else {
               FBoxSession recheck = mFBoxSessions.get(i);
               mAvailableFBoxSessionKeys.add(recheck.getKey());
               mFBoxSessions.remove(i);
            }
        } catch (Exception e) {
            System.out.println("removing board fault");
            FBoxSession recheck = mFBoxSessions.get(i);
            mAvailableFBoxSessionKeys.add(recheck.getKey());
            mFBoxSessions.remove(i);

        }
    }

    private class FBoxPersonalCloudSession {
        private Socket mSocket = null;
        private final FBox_Personal mKey;
        private String FBox_ID = "-1";
        private int mUserId = 0;
        private int mModeSharing = 0;  // 0 not sharing...

        FBoxPersonalCloudSession(Socket soc, FBox_Personal key) {
            mSocket = soc;
            mKey = key;
            FBox_ID = "-1";
            mModeSharing = 0;
        }

        private void setModeSharing(int mode) {
            mModeSharing = mode;
        }

        public int getModeSharing() {
            return mModeSharing;
        }

        public void setFBox_ID(String FBox_ID) {
            this.FBox_ID = FBox_ID;
        }

        public String getFBox_ID() {
            return FBox_ID;
        }

        public int getUserID() {
            return mUserId;
        }

        public void setUserID(int userID) {
            System.out.println("userid set:........"+userID);
            this.mUserId = userID;
        }

        public Socket getSocket() {
            return mSocket;
        }

        public FBox_Personal getKey() {
            return mKey;
        }

    }

    private void createPersonalCloud() {
        try {
            mFBoxPersonalCloudSocket = new ServerSocket(8081);
            mFBoxPersonalCloudSocket.setReuseAddress(true);
            mFBoxPersonalCloudSocket.setSoTimeout(200);
            System.out.println("created peronsal cloud server socket : " + "bound : " + mFBoxPersonalCloudSocket.isBound());
        } catch (SecurityException se) {
            se.printStackTrace();
        } catch (SocketException e) {
            try {
                if (mFBoxPersonalCloudSocket != null) {
                    mFBoxPersonalCloudSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxPersonalCloudSocket = null;
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (mFBoxPersonalCloudSocket != null) {
                Socket socket = mFBoxPersonalCloudSocket.accept();
                if (mAvailablePersonalCloudKeys.size() > 0) {
                    FBox_Personal key = mAvailablePersonalCloudKeys.get(0);
                    mAvailablePersonalCloudKeys.remove(0);
                    FBoxPersonalCloudSession session = new FBoxPersonalCloudSession(socket, key);
                    mFBoxPersonalCloudSessions.add(session);
                } else {
                    System.out.println("no key");
                    socket.close();
                    socket = null;
                }
            }
        } catch (SocketTimeoutException e) {

            //System.out.println("socket time out exception");

        } catch (SocketException e) {
            try {
                if (mFBoxPersonalCloudSocket != null) {
                    mFBoxPersonalCloudSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxPersonalCloudSocket = null;
            e.printStackTrace();
        } catch (IOException e) {
            try {
                if (mFBoxPersonalCloudSocket != null) {
                    mFBoxPersonalCloudSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxPersonalCloudSocket = null;
            e.printStackTrace();
        }
        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        receiveDataPersonalCloud();
                    }
                },
                65
        );

        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        acceptIncomingConnectionsPersonalCloud();
                    }
                },
                1000
        );
    }

    private void acceptIncomingConnectionsPersonalCloud() {
        try {
            if (mFBoxPersonalCloudSocket != null) {
                Socket socket = mFBoxPersonalCloudSocket.accept();
                System.out.println("accepted incoming cloud request. Socket : " + socket);
                System.out.println("socket bound to remote =>  (" + socket.getInetAddress().toString() + ":" + socket.getPort() + "), and Local => (" + socket.getLocalAddress() + ":" + socket.getLocalPort() + ")");
                if (mAvailablePersonalCloudKeys.size() > 0) {
                    FBox_Personal key = mAvailablePersonalCloudKeys.get(0);
                    mAvailablePersonalCloudKeys.remove(0);
                    FBoxPersonalCloudSession session = new FBoxPersonalCloudSession(socket, key);
                    if (session == null) {
                        System.out.println("newly created session is NULL");
                    }
                    mFBoxPersonalCloudSessions.add(session);
                } else {
                    System.out.println("this is null");
                    socket.close();
                    socket = null;
                }
            }
        } catch (SocketTimeoutException e) {
            //System.out.println("Accept TIMEOUT Occured");
        } catch (SocketException e) {
            try {
                if (mFBoxPersonalCloudSocket != null) {
                    mFBoxPersonalCloudSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxPersonalCloudSocket = null;
            System.out.println("socket exception Occured");
            e.printStackTrace();
            createFBoxServerSocket();
        } catch (IOException e) {
            //System.out.println("Accept IO exception Occured");
        }

        if(mFBoxPersonalCloudAcceptTimer != null) {
            mFBoxPersonalCloudAcceptTimer.cancel();
        }
        mFBoxPersonalCloudAcceptTimer = new Timer();
        mFBoxPersonalCloudAcceptTimer.schedule(new TimerTask() {
            public void run() {
                acceptIncomingConnectionsPersonalCloud();
            }

        },100);
    }

    private void receiveDataPersonalCloud() {
        try {
            for (int i = 0; i < mFBoxPersonalCloudSessions.size(); i++) {
                int sizel;
                BufferedInputStream inStream = new BufferedInputStream(mFBoxPersonalCloudSessions.get(i).getSocket().getInputStream());
                if (inStream == null) {
                    continue;
                }
                sizel = inStream.available();
                if(sizel > 0) {
                    byte[] bytes = new byte[sizel];
                    int x = inStream.read(bytes, 0, sizel);
                    if(mFBoxPersonalCloudSessions.get(i).getModeSharing() == 0) {
                        // if sharing not running...
                        String lMsg = new String(bytes);
                        System.out.println(lMsg);
                        String msg = retrieveMsg(lMsg);
                        System.out.println(msg);
                        if (msg.contains("FBoxSocketData")) {
                            mobileRequestPersonalCloud(msg,bytes,i);
                        } else if (msg.contains("MobileSocketData")) {
                            mobileRequestPersonalCloud(msg,bytes,i);
                        }
                    } else {
                        // sharing running send to mobile direct.....
                        String lMsg = new String(bytes);
                        String msg = retrieveMsg(lMsg);
                        if (msg.contains("MobileSocketData")) {
                            mobileRequestPersonalCloud(msg,bytes,i);
                        } else if (msg.contains("FBoxSocketData")) {
                            mobileRequestPersonalCloud(msg,bytes,i);
                        } else {
                            System.out.println("sending...." + bytes.length);
                            Socket sendSocket = null;
                            String FBoxId = mFBoxPersonalCloudSessions.get(i).getFBox_ID();
                            for (int k = 0; k < mFBoxPersonalCloudSessions.size(); k++) {
                                if (FBoxId.equalsIgnoreCase(mFBoxPersonalCloudSessions.get(k).getFBox_ID())
                                        && k != i) {
                                    System.out.println("match sending....");
                                    sendSocket = mFBoxPersonalCloudSessions.get(k).getSocket();
                                }
                            }
                            if (sendSocket != null) {
                                if(bytes.length <= 16 * 1024) {
                                    try {
                                        DataOutputStream outToServer = new DataOutputStream(sendSocket.getOutputStream());
                                        outToServer.write(bytes, 0, bytes.length);
                                        outToServer.flush();
                                    } catch (Exception e) {
                                        mAvailablePersonalCloudKeys.add(mFBoxPersonalCloudSessions.get(i).getKey());
                                        mFBoxPersonalCloudSessions.remove(i);
                                    }
                                } else {
                                    byte [] sendingBytes = Arrays.copyOfRange(bytes,0,16*1024);
                                    byte[] sendingBytes1 = Arrays.copyOfRange(bytes,16*1024,bytes.length);
                                    DataOutputStream outToServer = new DataOutputStream(sendSocket.getOutputStream());
                                    outToServer.write(sendingBytes, 0, sendingBytes.length);
                                    outToServer.flush();
                                    DataOutputStream outToServer1 = new DataOutputStream(sendSocket.getOutputStream());
                                    outToServer1.write(sendingBytes1, 0, sendingBytes1.length);
                                    outToServer1.flush();
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(mFBoxPersonalCloudReceiveTimer != null) {
            mFBoxPersonalCloudReceiveTimer.cancel();
        }
        mFBoxPersonalCloudReceiveTimer = new Timer();
        mFBoxPersonalCloudReceiveTimer.schedule(new TimerTask() {
            public void run() {
                receiveDataPersonalCloud();
            }

        },60);

    }

    private void mobileRequestPersonalCloud(String msg, byte [] bytes, int i) {
        try {
            if (msg.contains("SET_MODE")) {
                msg = msg.substring(msg.indexOf("mode: "));
                int mode = Integer.parseInt(msg.substring(6, msg.indexOf("\r\n")));
                String FBoxID = mFBoxPersonalCloudSessions.get(i).getFBox_ID();
                for (int k = 0; k < mFBoxPersonalCloudSessions.size(); k++) {
                    if(FBoxID.equalsIgnoreCase(mFBoxPersonalCloudSessions.get(k).getFBox_ID())) {
                        mFBoxPersonalCloudSessions.get(k).setModeSharing(mode);
                    }
                }
            } else if (msg.contains("Finish_Socket")) {
                try {
                    String FBoxID = mFBoxPersonalCloudSessions.get(i).getFBox_ID();
                    for (int k = 0; k < mFBoxPersonalCloudSessions.size(); k++) {
                        if(FBoxID.equalsIgnoreCase(mFBoxPersonalCloudSessions.get(k).getFBox_ID())) {
                            mFBoxPersonalCloudSessions.get(k).setModeSharing(0);
                        }
                        if(FBoxID.equalsIgnoreCase(mFBoxPersonalCloudSessions.get(k).getFBox_ID()) && mFBoxPersonalCloudSessions.get(k).getUserID() < 0) {
                            DataOutputStream outToServer=new DataOutputStream(mFBoxPersonalCloudSessions.get(k).getSocket().getOutputStream());
                            outToServer.write(bytes, 0, bytes.length);
                            outToServer.flush();
                        }
                    }
                    try {
                        mFBoxPersonalCloudSessions.get(i).getSocket().close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mAvailablePersonalCloudKeys.add(mFBoxPersonalCloudSessions.get(i).getKey());
                    mFBoxPersonalCloudSessions.remove(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (msg.contains("SETUP_DATA")) {
                checkServerClientExitOrNot(msg,i);
            } else {
                int UserId = mFBoxPersonalCloudSessions.get(i).getUserID();
                String FBoxId = mFBoxPersonalCloudSessions.get(i).getFBox_ID();
                for (int j = 0; j < mFBoxPersonalCloudSessions.size(); j++) {
                    System.out.println(" all fbox id" + mFBoxPersonalCloudSessions.get(j).getFBox_ID() + " current:" + FBoxId);
                System.out.println(" all user id" + mFBoxPersonalCloudSessions.get(j).getUserID() + " current:" + UserId);
                    if (mFBoxPersonalCloudSessions.get(j).getUserID() != UserId && mFBoxPersonalCloudSessions.get(j).getFBox_ID().equalsIgnoreCase(FBoxId)) {
                        try {
                            System.out.println("sending to personal cloud");
                            DataOutputStream outToServer = new DataOutputStream(mFBoxPersonalCloudSessions.get(j).getSocket().getOutputStream());
                            outToServer.write(bytes, 0, bytes.length);
                            outToServer.flush();
                        } catch (Exception e) {
                            mAvailablePersonalCloudKeys.add(mFBoxPersonalCloudSessions.get(i).getKey());
                            mFBoxPersonalCloudSessions.remove(i);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkServerClientExitOrNot(String msg, int i) {
        try {
            String str1 = msg.substring(msg.indexOf("FBox_ID"));
            String FboxID = str1.substring(8, str1.indexOf("\r\n"));
            int UserID = -1;
            if (msg.contains("User_ID")) {
                str1 = str1.substring(str1.indexOf("User_ID"));
                UserID = Integer.parseInt(str1.substring(8, str1.indexOf("\r\n")));
            }
            int connected = 0;
            for (int j = 0; j < mFBoxPersonalCloudSessions.size(); j++) {
                System.out.println(" all fbox id" + mFBoxPersonalCloudSessions.get(j).getFBox_ID() + " current:" + FboxID);
                System.out.println(" all user id" + mFBoxPersonalCloudSessions.get(j).getUserID() + " current:" + UserID);
                System.out.println("current loop:" + i);
                if (j == i) {
                    continue;
                }
                if (mFBoxPersonalCloudSessions.get(j).getFBox_ID().equalsIgnoreCase(FboxID)) {
                    System.out.println("match fbox");
                    if (mFBoxPersonalCloudSessions.get(j).getUserID() == UserID) {
                        System.out.println("match fbox user id");
                        BufferedOutputStream outStream = null;
                        String response = "Checking_Server";
                        String sendData = hideMsg(response);
                        try {
                            System.out.println("checking.....id");
                            outStream = new BufferedOutputStream(mFBoxPersonalCloudSessions.get(j).getSocket().getOutputStream());
                            outStream.write(sendData.getBytes(), 0, sendData.length());
                            outStream.flush();
                            outStream = null;
                            Thread.sleep(5000);
                            int sizel = 0;
                            BufferedInputStream inStream = new BufferedInputStream(mFBoxPersonalCloudSessions.get(j).getSocket().getInputStream());
                            if(inStream != null) {
                                sizel = inStream.available();
                            }
                            if(sizel > 0) {
                                System.out.println("already.....id");
                                byte[] bytes = new byte[sizel];
                                int x = inStream.read(bytes, 0, sizel);
                                // if sharing not running...
                                String output = retrieveMsg(new String(bytes));
                                System.out.println(output);
                                if(output.contains("Checking_Server")) {
                                    response = "CLOUD_USER_ALREADY";
                                    sendData = hideMsg(response);
                                    try {
                                        connected = 1;
                                        outStream = new BufferedOutputStream(mFBoxPersonalCloudSessions.get(i).getSocket().getOutputStream());
                                        outStream.write(sendData.getBytes(), 0, sendData.length());
                                        outStream.flush();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    System.out.println("no response match.....id");
                                    mFBoxPersonalCloudSessions.get(j).getSocket().close();
                                    mAvailablePersonalCloudKeys.add(mFBoxPersonalCloudSessions.get(j).getKey());
                                    mFBoxPersonalCloudSessions.remove(j);
                                    if (j < i) {
                                        i--;
                                    }
                                }
                            } else {
                                System.out.println("personal no response.....id");
                                mFBoxPersonalCloudSessions.get(j).getSocket().close();
                                mAvailablePersonalCloudKeys.add(mFBoxPersonalCloudSessions.get(j).getKey());
                                mFBoxPersonalCloudSessions.remove(j);
                                if (j < i) {
                                    i--;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("error.....id");
                            e.printStackTrace();
                            mFBoxPersonalCloudSessions.get(j).getSocket().close();
                            mAvailablePersonalCloudKeys.add(mFBoxPersonalCloudSessions.get(j).getKey());
                            mFBoxPersonalCloudSessions.remove(j);
                            if (j < i) {
                                i--;
                            }
                        }
                        break;
                    }
                }
            }
            if (connected == 0) {
                System.out.println("add .................");
                mFBoxPersonalCloudSessions.get(i).setFBox_ID(FboxID);
                mFBoxPersonalCloudSessions.get(i).setUserID(UserID);
                for (int j = 0; j < mFBoxPersonalCloudSessions.size(); j++) {
                    System.out.println(" all fbox id" + mFBoxPersonalCloudSessions.get(j).getFBox_ID());
                    System.out.println(" all user id" + mFBoxPersonalCloudSessions.get(j).getUserID());
                }
                try {
                    String sendData = hideMsg(msg);
                    BufferedOutputStream outStream = null;
                    outStream = new BufferedOutputStream(mFBoxPersonalCloudSessions.get(i).getSocket().getOutputStream());
                    outStream.write(sendData.getBytes(), 0, sendData.length());
                    outStream.flush();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("remove.....id");
                mFBoxPersonalCloudSessions.get(i).getSocket().close();
                mAvailablePersonalCloudKeys.add(mFBoxPersonalCloudSessions.get(i).getKey());
                mFBoxPersonalCloudSessions.remove(i);
            }
        } catch (Exception e) {
            System.out.println("exception id.....id"+e.toString());
            e.printStackTrace();
        }
    }

    private enum FBox_Sessions {
        kFBoxSession_1(2000),
        kFBoxSession_2(2100),
        kFBoxSession_3(2200),
        kFBoxSession_4(2300),
        kFBoxSession_5(2400),        // As of now we are limiting a maximum of 10 users to FBox
        kFBoxSession_6(2500),
        kFBoxSession_7(2600),
        kFBoxSession_8(2700),
        kFBoxSession_9(2800),
        kFBoxSession_10(2900);

        public int val;

        private FBox_Sessions(int value) {
            this.val = value;
        }
    }

    private class FBoxSession {
        private Socket mSocket = null;
        private final FBox_Sessions mKey;
        private String FBox_ID = "FBox@123";
        private String User_ID = "";
        private String mUserDatabase = "";
        HashMap<String, Socket> mMobileData = new HashMap<String, Socket>();
        private int mKeepAvailable = 0;
        private boolean mBoardSession = true;
        private String mBoardSSID;
        private String mBoardIP;

        FBoxSession(Socket soc, FBox_Sessions key) {
            mSocket = soc;
            mKey = key;
            FBox_ID = "FBox@123";
        }

        public void setBoardSSID(String ssid) {
            mBoardSSID = ssid;
        }

        public String getBoardSSID() {
            return mBoardSSID;
        }

        public void setBoardIP(String IP) {
            mBoardIP = IP;
        }

        public String getBoardIP() {
            return  mBoardIP;
        }

        public void setBoardSession(boolean boardSession) {
            mBoardSession = boardSession;
        }

        public boolean getBoardSession() {
            return mBoardSession;
        }

        public void setFBox_ID(String FBox_ID) {
            this.FBox_ID = FBox_ID;
        }

        public void setUserDatabase(String userDatabase) {
            this.mUserDatabase = userDatabase;
        }

        public String getUserDatabaseID() {
            return mUserDatabase;
        }

        public void setUser_ID(String User_ID) {
            this.User_ID = User_ID;
        }

        public String getFBox_ID() {
            return FBox_ID;
        }

        public String getUser_ID() {
            return User_ID;
        }

        public Socket getSocket() {
            return mSocket;
        }

        public FBox_Sessions getKey() {
            return mKey;
        }

        public int getKeepAvailable() {
            return mKeepAvailable;
        }

        public void setKeepAvailable(int value) {
            mKeepAvailable = value;
        }

    }

    public static void main(String args[]) throws Exception {
        final GCPServer newInstance = new GCPServer();
        newInstance.init();
        new Thread() {
            @Override
            public void run() {
                newInstance.createFBoxServerSocket();
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                newInstance.createPersonalCloud();
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                newInstance.checkAliveFBoxSession();
            }
        }.start();
    }

    private void init() {
        mFBoxPersonalCloudSessions = new ArrayList<>();
        mAvailablePersonalCloudKeys = new ArrayList<FBox_Personal>();
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_1);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_2);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_3);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_4);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_5);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_6);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_7);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_8);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_9);
        mAvailablePersonalCloudKeys.add(FBox_Personal.kFBoxSession_10);
        // fbox session..
        mFBoxSessions = new ArrayList<FBoxSession>();
        mAvailableFBoxSessionKeys = new ArrayList<FBox_Sessions>();
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_1);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_2);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_3);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_4);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_5);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_6);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_7);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_8);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_9);
        mAvailableFBoxSessionKeys.add(FBox_Sessions.kFBoxSession_10);

    }

    private void createFBoxServerSocket() {
        try {
            mFBoxServerSocket = new ServerSocket(8080);
            mFBoxServerSocket.setReuseAddress(true);
            mFBoxServerSocket.setSoTimeout(200);
            System.out.println("created FBox server socket : " + "bound : " + mFBoxServerSocket.isBound());
        } catch (SecurityException se) {
            se.printStackTrace();
        } catch (SocketException e) {
            try {
                if (mFBoxServerSocket != null) {
                    mFBoxServerSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxServerSocket = null;
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (mFBoxServerSocket != null) {
                Socket socket = mFBoxServerSocket.accept();
                if (mAvailableFBoxSessionKeys.size() > 0) {
                    FBox_Sessions key = mAvailableFBoxSessionKeys.get(0);
                    mAvailableFBoxSessionKeys.remove(0);
                    FBoxSession session = new FBoxSession(socket, key);
                    mFBoxSessions.add(session);
                } else {
                    System.out.println("no key");
                    socket.close();
                    socket = null;
                }
            }
        } catch (SocketTimeoutException e) {

            //System.out.println("socket time out exception");

        } catch (SocketException e) {
            try {
                if (mFBoxServerSocket != null) {
                    mFBoxServerSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxServerSocket = null;
            e.printStackTrace();
        } catch (IOException e) {
            try {
                if (mFBoxServerSocket != null) {
                    mFBoxServerSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxServerSocket = null;
            e.printStackTrace();
        }
        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        receiveData();
                    }
                },
                65
        );

        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        acceptIncomingConnections();
                    }
                },
                1000
        );
    }

    private void acceptIncomingConnections() {
        try {
            if (mFBoxServerSocket != null) {
                Socket socket = mFBoxServerSocket.accept();
                System.out.println("accepted incoming FBox request. Socket : " + socket);
                System.out.println("socket bound to remote =>  (" + socket.getInetAddress().toString() + ":" + socket.getPort() + "), and Local => (" + socket.getLocalAddress() + ":" + socket.getLocalPort() + ")");
                if (mAvailableFBoxSessionKeys.size() > 0) {
                    FBox_Sessions key = mAvailableFBoxSessionKeys.get(0);
                    mAvailableFBoxSessionKeys.remove(0);
                    FBoxSession session = new FBoxSession(socket, key);
                    if (session == null) {
                        System.out.println("newly created session is NULL");
                    }
                    mFBoxSessions.add(session);
                } else {
                    System.out.println("this is null");
                    socket.close();
                    socket = null;
                }
            }
        } catch (SocketTimeoutException e) {
            //System.out.println("Accept TIMEOUT Occured");
        } catch (SocketException e) {
            try {
                if (mFBoxServerSocket != null) {
                    mFBoxServerSocket.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            mFBoxServerSocket = null;
            System.out.println("socket exception Occured");
            e.printStackTrace();
            createFBoxServerSocket();
        } catch (IOException e) {
            //System.out.println("Accept IO exception Occured");
        }

        if(mFBoxServerAcceptTimer != null) {
            mFBoxServerAcceptTimer.cancel();
        }
        mFBoxServerAcceptTimer = new Timer();
        mFBoxServerAcceptTimer.schedule(new TimerTask() {
            public void run() {
                acceptIncomingConnections();
            }

        },100);
    }

    private void receiveData() {
        try {
            for (int i = 0; i < mFBoxSessions.size(); i++) {
                int sizel;
                BufferedInputStream inStream = new BufferedInputStream(mFBoxSessions.get(i).getSocket().getInputStream());
                if (inStream == null) {
                    continue;
                }
                sizel = inStream.available();
                if (sizel > 0) {
                    byte[] bytes = new byte[sizel];
                    int x = inStream.read(bytes, 0, sizel);
                    String lMsg = new String(bytes);
                    String msg = retrieveMsg(lMsg);
                    if (msg.contains("FBoxSocketData")) {
                        String data[] = msg.split("FBoxSocketData");
                        for (int k = 1; k < data.length; k++) {
                            fboxSendDataHandle("FBoxSocketData" + data[k], i,bytes,sizel);
                        }
                    } else if (msg.contains("MobileSocketData")) {
                        String data[] = msg.split("MobileSocketData");
                        System.out.println("size mobile:"+data.length+"\r\n"+msg);
                        for (int k = 1; k < data.length; k++) {
                            mobileRequestHandle("MobileSocketData" + data[k], i);
                        }

                    } else if (msg.contains("cloud_Db_exchange")) {
                        String str1 = msg.substring(msg.indexOf("FBox_ID"));
                        String FboxID = str1.substring(8, str1.indexOf("\r\n"));

                        String str2 = msg.substring(msg.indexOf("User_ID:"));
                        String UserID = str2.substring(8, str2.indexOf("\r\n"));

                        for (int j = 0; j < mFBoxSessions.size(); j++) {
                            if ((mFBoxSessions.get(j).getFBox_ID().equals(FboxID)) && (mFBoxSessions.get(j).getUser_ID().equals(UserID))) {
                                BufferedOutputStream outStream = null;
                                String sendData = hideMsg(msg);
                                try {
                                    outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                                    outStream.write(sendData.getBytes(), 0, sendData.length());
                                    outStream.flush();
                                } catch(Exception e) {
                                    mAvailableFBoxSessionKeys.add(mFBoxSessions.get(i).getKey());
                                    mFBoxSessions.remove(i);
                                }
                            }
                        }

                    } else if(msg.contains("FBoxSocketExchangeDB")) {
                        System.out.println("msg:"+msg);
                        if(msg.contains("SETUP_DB")) {
                            String str1 = msg.substring(msg.indexOf("User_ID"));
                            String userId = str1.substring(8, str1.indexOf("\r\n"));
                            mFBoxSessions.get(i).setUserDatabase(userId);
                        } else {
                            String userId = mFBoxSessions.get(i).getUserDatabaseID();
                            String FBoxId = mFBoxSessions.get(i).getFBox_ID();
                            for (int j = 0; j < mFBoxSessions.size(); j++) {
                                if(mFBoxSessions.get(j).getUser_ID().equals(userId)&&mFBoxSessions.get(j).getFBox_ID().equals(FBoxId)) {
                                    try {
                                        BufferedOutputStream outStream = null;
                                        outStream = new BufferedOutputStream(mFBoxSessions.get(j).getSocket().getOutputStream());
                                        outStream.write(bytes, 0, sizel);
                                        outStream.flush();
                                    } catch(Exception e) {
                                        int index = j;
                                        try {
                                            if (mFBoxSessions.get(index).getSocket() != null) {
                                                mFBoxSessions.get(index).getSocket().close();
                                            }
                                        } catch (Exception e1) {
                                            e1.printStackTrace();
                                        }
                                        mAvailableFBoxSessionKeys.add(mFBoxSessions.get(index).getKey());
                                        mFBoxSessions.remove(index);
                                    }
                                }
                            }
                            if(msg.contains("doneDBExchange")) {
                                mFBoxSessions.get(i).setUserDatabase("");
                            }
                        }

                    } else {
                        String userId = mFBoxSessions.get(i).getUserDatabaseID();
                        String FBoxId = mFBoxSessions.get(i).getFBox_ID();
                        for (int j = 0; j < mFBoxSessions.size(); j++) {
                            if(userId.length() <= 0) {
                                break;
                            }
                            System.out.println("current :"+userId+", original:"+mFBoxSessions.get(j).getUser_ID());
                            if(mFBoxSessions.get(j).getUser_ID().equals(userId)&&mFBoxSessions.get(j).getFBox_ID().equals(FBoxId)) {
                                System.out.println("sending current:"+userId+", original:"+mFBoxSessions.get(j).getUser_ID());
                                try {
                                    BufferedOutputStream outStream = null;
                                    outStream = new BufferedOutputStream(mFBoxSessions.get(j).getSocket().getOutputStream());
                                    outStream.write(bytes, 0, sizel);
                                    outStream.flush();
                                } catch(Exception e) {
                                    int index = j;
                                    try {
                                        if (mFBoxSessions.get(index).getSocket() != null) {
                                            mFBoxSessions.get(index).getSocket().close();
                                        }
                                    } catch (Exception e1) {
                                        e1.printStackTrace();
                                    }
                                    mAvailableFBoxSessionKeys.add(mFBoxSessions.get(index).getKey());
                                    mFBoxSessions.remove(index);
                                }
                            }
                        }
                    }
                } else {
//                System.out.println("No Data to read for RTSP Client : " + mFBoxSessions.get(i).getKey());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(mFBoxServerReceiveTimer != null) {
            mFBoxServerReceiveTimer.cancel();
        }
        mFBoxServerReceiveTimer = new Timer();
        mFBoxServerReceiveTimer.schedule(new TimerTask() {
            public void run() {
                receiveData();
            }

        },60);

    }

    private void fboxSendDataHandle(String msg, int i,byte[] sending,int sizeOfData) {
        if(mFBoxSessions.size() <=i) {
            System.out.println("error check");
            return;
        }
        System.out.println("this:"+msg);
        try {
            if (msg.contains("SETUP_DATA")) {
                String str1 = msg.substring(msg.indexOf("FBox_ID"));
                String FboxID = str1.substring(8, str1.indexOf("\r\n"));
                String ssid = "";
                try {
                    String str2 = msg.substring(msg.indexOf("SSID: "));
                    ssid = str2.substring(6, str2.indexOf("\r\n"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String ipAddress = "";
                try {
                    String str3 = msg.substring(msg.indexOf("IP: "));
                    ipAddress = str3.substring(4, str3.indexOf("\r\n"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                int remove = 0;
                int index = 0;
                //int fbox_id = Integer.parseInt(FboxID);
                System.out.println("mFBox size: "+mFBoxSessions.size());
                for (int j = 0; j < mFBoxSessions.size(); j++) {
                    System.out.println("list id: "+mFBoxSessions.get(j).getFBox_ID()+" and id: "+mFBoxSessions.get(j).getUser_ID());
                    System.out.println("current id: "+FboxID);
                    if (mFBoxSessions.get(j).getFBox_ID().equals(FboxID) && mFBoxSessions.get(j).getUser_ID().equals("-1")) {
                        System.out.println("match time:"+j);
                        if(remove == 0) {
                            index = j;
                        }
                        remove++;
                    }
                }
                try {
                    mFBoxSessions.get(i).setFBox_ID(FboxID);
                    mFBoxSessions.get(i).setUser_ID("-1");
                    mFBoxSessions.get(i).setKeepAvailable(0);
                    mFBoxSessions.get(i).setBoardSession(true);
                    mFBoxSessions.get(i).setBoardSSID(ssid);
                    mFBoxSessions.get(i).setBoardIP(ipAddress);
                    if(remove == 1) {
                        try {
                            if (mFBoxSessions.get(index).getSocket() != null) {
                                mFBoxSessions.get(index).getSocket().close();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mAvailableFBoxSessionKeys.add(mFBoxSessions.get(index).getKey());
                        mFBoxSessions.remove(index);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }

            } else if (msg.contains("KEEP_ALIVE")) {
                mFBoxSessions.get(i).setKeepAvailable(0);
                String response = "KEEP_ALIVE";
                BufferedOutputStream outStream = null;
                String sendData = hideMsg(response+"\r\n"+msg);
                try {
                    outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                    outStream.write(sendData.getBytes(), 0, sendData.length());
                    outStream.flush();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }  else if(msg.contains("FBoxDataBaseSocket")) {
                System.out.println("FBox:\n\n\n"+msg);
                String str1 = msg.substring(msg.indexOf("FBox_ID:"));
                String FboxID = str1.substring(8, str1.indexOf("\r\n"));
                String str2 = msg.substring(msg.indexOf("User_Id:"));
                String UserID = str2.substring(8, str2.indexOf("\r\n"));
                System.out.println("fbox size:  "+mFBoxSessions.size());
                for (int j = 0; j < mFBoxSessions.size();j++) {
                    System.out.println("current fboxId:"+FboxID+",original id:"+mFBoxSessions.get(j).getFBox_ID()+", current userID:"+UserID+",original id:"+mFBoxSessions.get(j).getUser_ID());
                    if (mFBoxSessions.get(j).getFBox_ID().equals(FboxID) && mFBoxSessions.get(j).getUser_ID().equals(UserID)) {
                        System.out.println("sending to mobile..............");
                        Socket data = mFBoxSessions.get(j).getSocket();
                        BufferedOutputStream outStream = null;
                        outStream = new BufferedOutputStream(data.getOutputStream());
                        outStream.write(sending, 0, sizeOfData);
                        outStream.flush();
                        break;
                    } else {
                        System.out.println("no match..............");
                    }
                }
            } else if (msg.contains("TEARDOWN")) {
                String str1 = msg.substring(msg.indexOf("FBox_ID"));
                String FboxID = str1.substring(8, str1.indexOf("\r\n"));
                for (int j = 0; j < mFBoxSessions.size(); j++) {
                    if (mFBoxSessions.get(j).getFBox_ID().equals(FboxID)) {
                        if (mFBoxSessions.get(j).getUser_ID().equals(-1)) {
                            if (mFBoxSessions.get(j).getSocket() != null) {
                                mFBoxSessions.get(j).getSocket().close();
                            }
                            mAvailableFBoxSessionKeys.add(mFBoxSessions.get(j).getKey());
                            mFBoxSessions.remove(j);
                        } else {
                            String response = "CLOUD_SEND_FAILED";
                            try {
                                BufferedOutputStream outStream = null;
                                String sendData = hideMsg(response);
                                outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                                outStream.write(sendData.getBytes(), 0, sendData.length());
                                outStream.flush();
                                if (mFBoxSessions.get(j).getSocket() != null) {
                                    mFBoxSessions.get(j).getSocket().close();
                                }
                                mAvailableFBoxSessionKeys.add(mFBoxSessions.get(j).getKey());
                                mFBoxSessions.remove(j);
                            } catch(Exception e) {
                                if (mFBoxSessions.get(j).getSocket() != null) {
                                    mFBoxSessions.get(j).getSocket().close();
                                }
                                mAvailableFBoxSessionKeys.add(mFBoxSessions.get(j).getKey());
                                mFBoxSessions.remove(j);
                            }
                        }
                    }
                }

            } else {
                String str1 = msg.substring(msg.indexOf("FBox_ID:"));
                String FboxID = str1.substring(8, str1.indexOf("\r\n"));
                String user_id = str1.substring(str1.indexOf("User_ID:"));
                String UserID = user_id.substring(8, user_id.indexOf("\r\n"));
                System.out.println("current fbox:"+FboxID+", user id:"+UserID);
                Socket data1 =  mFBoxSessions.get(i).mMobileData.get(UserID);
                if(!UserID.equalsIgnoreCase("AllCloudData") && data1 != null) {
                    Socket data =  mFBoxSessions.get(i).mMobileData.get(UserID);
                    BufferedOutputStream outStream = null;
                    String lMsg = hideMsg(msg);
                    outStream = new BufferedOutputStream(data.getOutputStream());
                    outStream.write(lMsg.getBytes(), 0, lMsg.length());
                    outStream.flush();
                } else {
                    for (int j = 0; j < mFBoxSessions.size(); j++) {
                        System.out.println("loop fboxid :" + mFBoxSessions.get(j).getFBox_ID() + ", user id:" + mFBoxSessions.get(j).getUser_ID());
                        try {
                            if (UserID.equals("AllCloudData") && (mFBoxSessions.get(j).getFBox_ID().equals(FboxID)) && (!mFBoxSessions.get(j).getUser_ID().equals("-1"))) {
                                BufferedOutputStream outStream = null;
                                String lMsg = hideMsg(msg);
                                outStream = new BufferedOutputStream(mFBoxSessions.get(j).getSocket().getOutputStream());
                                outStream.write(lMsg.getBytes(), 0, lMsg.length());
                                outStream.flush();
                            } else if ((mFBoxSessions.get(j).getFBox_ID().equals(FboxID)) && (mFBoxSessions.get(j).getUser_ID().equals(UserID))) {
                                BufferedOutputStream outStream = null;
                                String lMsg = hideMsg(msg);
                                outStream = new BufferedOutputStream(mFBoxSessions.get(j).getSocket().getOutputStream());
                                outStream.write(lMsg.getBytes(), 0, lMsg.length());
                                outStream.flush();
                            } else {
                                System.out.println("no match");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            if (mFBoxSessions.get(j).getSocket() != null) {
                                mFBoxSessions.get(j).getSocket().close();
                            }
                            mAvailableFBoxSessionKeys.add(mFBoxSessions.get(j).getKey());
                            mFBoxSessions.remove(j);
                            // remove key if not present or exception.
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mobileRequestHandle(String msg, int i) {
        System.out.println("msg:"+msg);
        try {
            if (msg.contains("SETUP_DATA")) {
                String str1 = msg.substring(msg.indexOf("FBox_ID"));
                String FboxID = str1.substring(8, str1.indexOf("\r\n"));
                //int fbox_id = Integer.parseInt(FboxID);
                str1 = str1.substring(str1.indexOf("User_ID"));
                String UserID = str1.substring(8, str1.indexOf("\r\n"));
                // check dublicate....
                for(int m = 0; m < mFBoxSessions.size(); m++) {
                    if(m == i) {
                        continue;
                    }
                    if ((mFBoxSessions.get(m).getFBox_ID().equals(FboxID)) && (mFBoxSessions.get(m).getUser_ID().equals(UserID))) {
                        BufferedOutputStream outStream = null;
                        try {
                            String response = "Checking_Server";
                            String sendData = hideMsg(response);
                            outStream = null;
                            outStream = new BufferedOutputStream(mFBoxSessions.get(m).getSocket().getOutputStream());
                            outStream.write(sendData.getBytes(), 0, sendData.length());
                            outStream.flush();
                            outStream = null;
                            Thread.sleep(200);
                            int sizel = 0;
                            BufferedInputStream inStream = new BufferedInputStream(mFBoxSessions.get(m).getSocket().getInputStream());
                            if (inStream != null) {
                                sizel = inStream.available();
                            }
                            if(sizel > 0) {
                                System.out.println("already.....id");
                                byte[] bytes = new byte[sizel];
                                int x = inStream.read(bytes, 0, sizel);
                                // if sharing not running...
                                String output = retrieveMsg(new String(bytes));
                                System.out.println(output);
                                if (output.contains("Checking_Server")) {
                                    response = "CLOUD_USER_ALREADY";
                                    sendData = hideMsg(response);
                                    try {
                                        outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                                        outStream.write(sendData.getBytes(), 0, sendData.length());
                                        outStream.flush();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    if (mFBoxSessions.get(i).getSocket() != null) {
                                        mFBoxSessions.get(i).getSocket().close();
                                    }
                                    mAvailableFBoxSessionKeys.add(mFBoxSessions.get(i).getKey());
                                    mFBoxSessions.remove(i);
                                    System.out.println("user already connected.." + mFBoxSessions.size());
                                    return;
                                } else {
                                    mAvailableFBoxSessionKeys.add(mFBoxSessions.get(m).getKey());
                                    mFBoxSessions.remove(m);
                                    if(m < i) {
                                        i--;
                                    }
                                    System.out.println("not match filed.." + mFBoxSessions.size());
                                }
                            } else {
                                mAvailableFBoxSessionKeys.add(mFBoxSessions.get(m).getKey());
                                mFBoxSessions.remove(m);
                                if(m < i) {
                                    i--;
                                }
                                System.out.println("no response.." + mFBoxSessions.size());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            mAvailableFBoxSessionKeys.add(mFBoxSessions.get(m).getKey());
                            mFBoxSessions.remove(m);
                            if(m < i) {
                                i--;
                            }
                            System.out.println("fialed user already connected.." + mFBoxSessions.size());
                        }
                        break;
                    }
                }
                mFBoxSessions.get(i).setFBox_ID(FboxID);
                mFBoxSessions.get(i).setUser_ID(UserID);
                mFBoxSessions.get(i).setBoardSession(false);
                mFBoxSessions.get(i).setKeepAvailable(0);
                if(msg.contains("setFbox")) {
                    for(int m = 0; m < mFBoxSessions.size(); m++) {
                        FBoxSession cur = mFBoxSessions.get(m);
                        System.out.println("not matching............"+cur.getFBox_ID()+",,,,,"+FboxID+",,,,,,"+cur.getUser_ID());
                        if ((cur.getFBox_ID().equals(FboxID)) && (cur.getUser_ID().equalsIgnoreCase("-1"))) {
                            System.out.println("matching............");
                            cur.mMobileData.put(UserID,mFBoxSessions.get(i).getSocket());
                            try {
                                BufferedOutputStream outStream = null;
                                String response = " SETUP_DATA:SUCCESSFULLY:-DONE\r\n";
                                response += "SSID: "+cur.getBoardSSID()+"\r\n";
                                response += "IP: "+cur.getBoardIP()+"\r\n\r\n";
                                String sendData = hideMsg(response);
                                outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                                outStream.write(sendData.getBytes(), 0, sendData.length());
                                outStream.flush();
                            } catch(Exception e) {
                                if (mFBoxSessions.get(i).getSocket() != null) {
                                    mFBoxSessions.get(i).getSocket().close();
                                }
                                mAvailableFBoxSessionKeys.add(mFBoxSessions.get(i).getKey());
                                mFBoxSessions.remove(i);
                            }
                            return;
                        }
                    }
                    try {
                        BufferedOutputStream outStream = null;
                        String response = "CLOUD_SEND_FAILED";
                        String sendData = hideMsg(response);
                        outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                        outStream.write(sendData.getBytes(), 0, sendData.length());
                        outStream.flush();
                        if (mFBoxSessions.get(i).getSocket() != null) {
                            mFBoxSessions.get(i).getSocket().close();
                        }
                        mAvailableFBoxSessionKeys.add(mFBoxSessions.get(i).getKey());
                        mFBoxSessions.remove(i);
                    } catch(Exception e) {
                        if (mFBoxSessions.get(i).getSocket() != null) {
                            mFBoxSessions.get(i).getSocket().close();
                        }
                        mAvailableFBoxSessionKeys.add(mFBoxSessions.get(i).getKey());
                        mFBoxSessions.remove(i);
                    }
                }
            } else if (msg.contains("KEEP_ALIVE")) {
                System.out.println("keep_alive");
                mFBoxSessions.get(i).setKeepAvailable(0);
                String response = "KEEP_ALIVE";
                BufferedOutputStream outStream = null;
                String sendData = hideMsg(response+"\r\n"+msg);
                try {
                    outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                    outStream.write(sendData.getBytes(), 0, sendData.length());
                    outStream.flush();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            } else if (msg.contains("TEARDOWN")) {
                String str1 = msg.substring(msg.indexOf("CSeq:"));
                String cSeq = str1.substring(6, str1.indexOf("\r\n"));

                String str2 = msg.substring(msg.indexOf("FBox_ID:"));
                String FboxID = str2.substring(8, str2.indexOf("\r\n"));
                //int FBox_id = Integer.parseInt(FboxID);

                String str3 = msg.substring(msg.indexOf("User_ID:"));
                String userIdstr = str3.substring(8, str3.indexOf("\r\n"));

                for (int j = 0; j < mFBoxSessions.size(); j++) {
                    if ((mFBoxSessions.get(j).getFBox_ID().equals(FboxID)) && (mFBoxSessions.get(j).getUser_ID().equals(userIdstr))) {
                        String response = "RTSP/1.0 200 OK \r\nServer: FBox/Android\r\nCSeq: " + cSeq + "\r\n\r\nstopFBoxServer : 2000";
                        BufferedOutputStream outStream = null;
                        String sendData = hideMsg(response);
                        try {
                            if(!msg.contains("OnlyCloudClose")) {
                                outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                                outStream.write(sendData.getBytes(), 0, sendData.length());
                                outStream.flush();
                            }
                        } catch(Exception e) {
                        }
                        if (mFBoxSessions.get(i).getSocket() != null) {
                            mFBoxSessions.get(i).getSocket().close();
                        }
                        System.out.println(""+mFBoxSessions.get(j).getUser_ID());
                        mAvailableFBoxSessionKeys.add(mFBoxSessions.get(i).getKey());
                        mFBoxSessions.remove(i);
                    }
                }

            } else {
                String str1 = msg.substring(msg.indexOf("FBox_ID"));
                String FboxID = str1.substring(8, str1.indexOf("\r\n"));
                //int FBox_id = Integer.parseInt(FboxID);
                int output = 0;
                for (int j = 0; j < mFBoxSessions.size(); j++) {
                    try {
                        System.out.println("fbox id"+mFBoxSessions.get(j).getFBox_ID()+", id"+mFBoxSessions.get(j).getUser_ID());
                        System.out.println("fbox current id "+ FboxID);
                        if ((mFBoxSessions.get(j).getFBox_ID().equals(FboxID)) && (mFBoxSessions.get(j).getUser_ID().equals("-1"))) {
                            System.out.println("match id: "+msg);
                            BufferedOutputStream outStream = null;
                            String lMsg = hideMsg(msg);
                            outStream = new BufferedOutputStream(mFBoxSessions.get(j).getSocket().getOutputStream());
                            outStream.write(lMsg.getBytes(), 0, lMsg.length());
                            outStream.flush();
                            output = 1;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (mFBoxSessions.get(j).getSocket() != null) {
                            mFBoxSessions.get(j).getSocket().close();
                        }
                        mAvailableFBoxSessionKeys.add(mFBoxSessions.get(j).getKey());
                        mFBoxSessions.remove(j);
                    }
                }
                if (output != 1) {
                    BufferedOutputStream outStream = null;
                    String response = "CLOUD_SEND_FAILED";
                    String sendData = hideMsg(response);
                    try {
                        outStream = new BufferedOutputStream(mFBoxSessions.get(i).getSocket().getOutputStream());
                        outStream.write(sendData.getBytes(), 0, sendData.length());
                        outStream.flush();
                    }catch(Exception e) {}
                    if (mFBoxSessions.get(i).getSocket() != null) {
                        mFBoxSessions.get(i).getSocket().close();
                    }
                    mAvailableFBoxSessionKeys.add(mFBoxSessions.get(i).getKey());
                    mFBoxSessions.remove(i);

                    System.out.println("failed" + mFBoxSessions.size());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String hideMsg(String msg) {
        byte arr[] = msg.getBytes();
        for (int index = 0; index < arr.length; index++) {
            byte temp = (byte) (0x7F - arr[index]);
            arr[index] = temp;
        }
        String temp = new String(arr);
        return temp;
    }

    public String retrieveMsg(String msg) {
        byte[] arr = msg.getBytes();
        for (int index = 0; index < arr.length; index++) {
            byte temp = (byte) (0x7F - arr[index]);
            arr[index] = temp;
        }
        String temp = new String(arr);
        return temp;
    }


}



