/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Zebra Technologies - initial API and implementation
 *     Sierra Wireless, - initial API and implementation
 *     Bosch Software Innovations GmbH, - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.client.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.DeregisterRequest;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.RegisterResponse;
import org.eclipse.leshan.core.response.WriteResponse;

/*
 * To build: 
 * mvn assembly:assembly -DdescriptorId=jar-with-dependencies
 * To use:
 * java -jar target/leshan-client-*-SNAPSHOT-jar-with-dependencies.jar 127.0.0.1 5683
 */
public class LeshanClientExample {
    private String registrationID;
    private final Location locationInstance = new Location();
    private final ParkingSpot ParkingSpotInstance = new ParkingSpot();
    private final MultipleAxisJoystickObject MAJInstance = new MultipleAxisJoystickObject();
    private final AddressableTextDisplayObject ATDInstance = new AddressableTextDisplayObject();
    private final UpdateObject updateInstance = new UpdateObject();

    public static BufferedReader inp;
    public static BufferedWriter out;
    // public static Scanner out;
    public static String piState = "free";

    public static PipedOutputStream output;
    public static PipedInputStream input;

    public static void print(String s) {
        System.out.println(s);
    }

    public static void pipe(String msg) {
        try {
            out.write(msg + "\n");
            out.flush();
        } catch (Exception err) {

        }
    }

    public static void updateModuleStatus(String s) {
        pipe("disp:" + s + "\n");
        System.out.println("Print: " + "disp:" + s);
    }

    public static void updateDisplaySettings(String s) {
        pipe("conf:" + s);
    }

    public static void main(final String[] args) {
        int endPointId = 7;
        int localPt = 4000;
        System.out.println("Client start: " + System.currentTimeMillis());
        if (args.length == 4)
            new LeshanClientExample(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), endPointId);
        else if (args.length == 3) {
            for (int k = 0; k < Integer.parseUnsignedInt(args[2]); k++) {
                new LeshanClientExample("0", localPt, args[0], Integer.parseInt(args[1]), endPointId);
                localPt = localPt + 1;
                endPointId = endPointId + 1;
                // Thread.sleep(100);
            }
        } else if (args.length == 2)
            new LeshanClientExample("0", 0, args[0], Integer.parseInt(args[1]), endPointId);
        else {
            String hostIP = null;
            int hostPort = 5683;
            String service_name = "BrokerService";

            JmDNS jmdns = null;
            try {
                System.out.println("Searching for server..");
                jmdns = JmDNS.create();
                while (true) {

                    ServiceInfo[] infos = jmdns.list("_coap._udp.local.", hostPort);
                    System.out.println("List _coap._udp.local.");
                    if (infos.length > 0) {
                        System.out.println("Found " + infos.length + " server(s).! Selected server-1.");
                        for (int i = 0; i < infos.length; i++) {
                            System.out.println("mDNS " + i + " : " + infos[i].getHostAddress() + "subtype: "
                                    + infos[0].getSubtype());
                            // if (infos[0].getSubtype().compareTo(service_name) == 0)
                            {
                                hostIP = infos[0].getHostAddress();
                                new LeshanClientExample("0", 0, hostIP, hostPort, endPointId);
                                break;
                            }
                        }

                    }

                    /*
                     * try { // Thread.sleep(5000); } catch (InterruptedException e) { // TODO Auto-generated catch
                     * block e.printStackTrace(); }
                     */
                    System.out.println("No servers found.!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if (jmdns != null)
                    try {
                        jmdns.close();
                    } catch (IOException exception) {
                        //
                    }
            }

        }

    }

    public LeshanClientExample(final String localHostName, final int localPort, final String serverHostName,
            final int serverPort, final int endPointId) {

        // Initialize object list
        ObjectsInitializer initializer = new ObjectsInitializer();

        initializer.setClassForObject(3, Device.class);
        initializer.setInstancesForObject(6, locationInstance);
        initializer.setInstancesForObject(5, updateInstance);

        initializer.setInstancesForObject(32700, ParkingSpotInstance);
        initializer.setInstancesForObject(3345, MAJInstance);
        initializer.setInstancesForObject(3341, ATDInstance);

        List<ObjectEnabler> enablers = initializer.createMandatory();
        enablers.add(initializer.create(6));

        // Create client
        final InetSocketAddress clientAddress = new InetSocketAddress(localHostName, localPort);
        final InetSocketAddress serverAddress = new InetSocketAddress(serverHostName, serverPort);

        final LeshanClient client = new LeshanClient(clientAddress, serverAddress, enablers);

        // Start the client
        client.start();

        // Register to the server
        final String endpointIdentifier = "Parking-Spot-" + endPointId;
        // UUID.randomUUID().toString();
        RegisterResponse response = client.send(new RegisterRequest(endpointIdentifier));
        if (response == null) {
            System.out.println("Registration request timeout");
            return;
        }

        System.out.println("Device Registration (Success? " + response.getCode() + ")");
        if (response.getCode() != ResponseCode.CREATED) {
            // TODO Should we have a error message on response ?
            // System.err.println("\tDevice Registration Error: " + response.getErrorMessage());
            System.err.println(
                    "If you're having issues connecting to the LWM2M endpoint, try using the DTLS port instead");
            return;
        }

        registrationID = response.getRegistrationID();
        System.out.println("\tDevice: Registered Client Location '" + registrationID + "'");

        // Deregister on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (registrationID != null) {
                    System.out.println("\tDevice: Deregistering Client '" + registrationID + "'");
                    client.send(new DeregisterRequest(registrationID), 1000);
                    client.stop();
                }
            }
        });
        System.out.println("Server started successfully.!");
        System.out.println("Client " + endpointIdentifier + " is up.!");
        System.out.println("Creating pipe to SenseHat module.");

        String cmd = "/usr/bin/python /home/pi/iot_workspace/argtest.py";
        String cmd2 = "/usr/bin/python /home/pi/iot_workspace/module.py";
        System.out.println("Client registered: " + System.currentTimeMillis());
        try {
            print(cmd);
            print(System.getProperty("user.dir"));
            Process p = Runtime.getRuntime().exec(cmd);
            Process p2 = Runtime.getRuntime().exec(cmd2);

            out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
            System.out.println("Communication pipe established with SenseHat.!");

            Scanner sc = new Scanner(p2.getInputStream());
            while (true) {
                while (sc.hasNextLine()) {

                    String input = sc.nextLine(); // String input = sc.nextLine();

                    if (input != null) {
                        System.out.println("PYTHON: " + input);
                        String[] st = input.split(":");
                        if (st.length == 2)
                            System.out.println("PYTHON: Joystick event. (data: " + input + ") len:" + st.length
                                    + " st[0]: " + st[0] + " st[1]: " + st[1]);
                        if (st.length == 2 && st[0].compareTo("state") == 0) {
                            MAJInstance.updateYaxisChange(st[1]);
                        }
                    }
                    Thread.sleep(50);
                }

            }
        } catch (Exception err) {
            System.out.println("Unable to create pipe.!");
            err.printStackTrace();
        }
    }

    public static class Device extends BaseInstanceEnabler {

        public Device() {
            // notify new date each 5 second
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    fireResourcesChange(13);
                }
            }, 5000, 5000);
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Device Resource " + resourceid);
            switch (resourceid) {
            case 0:
                return ReadResponse.success(resourceid, getManufacturer());
            case 1:
                return ReadResponse.success(resourceid, getModelNumber());
            case 2:
                return ReadResponse.success(resourceid, getSerialNumber());
            case 3:
                return ReadResponse.success(resourceid, getFirmwareVersion());
            case 9:
                return ReadResponse.success(resourceid, getBatteryLevel());
            case 10:
                return ReadResponse.success(resourceid, getMemoryFree());
            case 11:
                Map<Integer, Long> errorCodes = new HashMap<>();
                errorCodes.put(0, getErrorCode());
                return ReadResponse.success(resourceid, errorCodes, Type.INTEGER);
            case 13:
                return ReadResponse.success(resourceid, getCurrentTime());
            case 14:
                return ReadResponse.success(resourceid, getUtcOffset());
            case 15:
                return ReadResponse.success(resourceid, getTimezone());
            case 16:
                return ReadResponse.success(resourceid, getSupportedBinding());
            default:
                return super.read(resourceid);
            }
        }

        @Override
        public ExecuteResponse execute(int resourceid, String params) {
            System.out.println("Execute on Device resource " + resourceid);
            if (params != null && params.length() != 0)
                System.out.println("\t params " + params);
            return ExecuteResponse.success();
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on Device Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 13:
                return WriteResponse.notFound();
            case 14:
                setUtcOffset((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            case 15:
                setTimezone((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            default:
                return super.write(resourceid, value);
            }
        }

        private String getManufacturer() {
            return "Leshan Example Device";
        }

        private String getModelNumber() {
            return "Model 500";
        }

        private String getSerialNumber() {
            return "LT-500-000-0001";
        }

        private String getFirmwareVersion() {
            return "1.0.0";
        }

        private long getErrorCode() {
            return 0;
        }

        private int getBatteryLevel() {
            final Random rand = new Random();
            return rand.nextInt(100);
        }

        private int getMemoryFree() {
            final Random rand = new Random();
            return rand.nextInt(50) + 114;
        }

        private Date getCurrentTime() {
            return new Date();
        }

        private String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());;

        private String getUtcOffset() {
            return utcOffset;
        }

        private void setUtcOffset(String t) {
            utcOffset = t;
        }

        private String timeZone = TimeZone.getDefault().getID();

        private String getTimezone() {
            return timeZone;
        }

        private void setTimezone(String t) {
            timeZone = t;
        }

        private String getSupportedBinding() {
            return "U";
        }
    }

    public static class Location extends BaseInstanceEnabler {
        private Random random;
        private float latitude;
        private float longitude;
        private Date timestamp;

        public Location() {
            random = new Random();
            latitude = Float.valueOf(random.nextInt(180));
            longitude = Float.valueOf(random.nextInt(360));
            timestamp = new Date();
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Location Resource " + resourceid);
            switch (resourceid) {
            case 0:
                return ReadResponse.success(resourceid, getLatitude());
            case 1:
                return ReadResponse.success(resourceid, getLongitude());
            case 5:
                return ReadResponse.success(resourceid, getTimestamp());
            default:
                return super.read(resourceid);
            }
        }

        /*
         * public void moveLocation(String nextMove) { switch (nextMove.charAt(0)) { case 'w': moveLatitude(1.0f);
         * break; case 'a': moveLongitude(-1.0f); break; case 's': moveLatitude(-1.0f); break; case 'd':
         * moveLongitude(1.0f); break; } }
         * 
         * private void moveLatitude(float delta) { latitude = latitude + delta; // timestamp = new Date();
         * fireResourcesChange(0, 5); }
         * 
         * private void moveLongitude(float delta) { longitude = longitude + delta; // timestamp = new Date();
         * fireResourcesChange(1, 5); }
         */
        public String getLatitude() {
            return Float.toString(latitude - 90.0f);
        }

        public String getLongitude() {
            return Float.toString(longitude - 180.f);
        }

        public Date getTimestamp() {
            return timestamp;
        }
    }

    public static class ParkingSpot extends BaseInstanceEnabler {
        private String parkingSpotState;
        private String VehicleID;
        private double ParkingRate;
        private String parkingSpotId;

        public ParkingSpot() {
            parkingSpotState = "free";
            parkingSpotId = "Parking-Spot-7";
            VehicleID = "";
            ParkingRate = 0;
            // notify new date each 5 second
            /*
             * Timer timer = new Timer(); timer.schedule(new TimerTask() {
             * 
             * @Override public void run() { fireResourcesChange(13); } }, 5000, 5000);
             */
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Device Resource " + resourceid);
            switch (resourceid) {
            case 32800:
                return ReadResponse.success(resourceid, getParkingSpotID());
            case 32801:
                return ReadResponse.success(resourceid, getParkingSpotState());
            case 32802:
                return ReadResponse.success(resourceid, getVehicleID());
            case 32803:
                return ReadResponse.success(resourceid, getParkingRate());
            default:
                return super.read(resourceid);
            }
        }

        /*
         * @Override public ExecuteResponse execute(int resourceid, String params) { System.out.println(
         * "Execute on Device resource " + resourceid); if (params != null && params.length() != 0) System.out.println(
         * "\t params " + params); return ExecuteResponse.success(); }
         */

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on Device Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 32801:
                setParkingSpotState((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            case 32802:
                setVehicleID((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            case 32803:
                setParkingRate((Double) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            default:
                return super.write(resourceid, value);
            }
        }

        private String getParkingSpotID() {
            return parkingSpotId;
        }

        private String getParkingSpotState() {
            return parkingSpotState;
        }

        private void setParkingSpotState(String s) {
            parkingSpotState = s;
            if (s.compareTo("reserve") == 0)
                pipe("disp:orange" + "\n");
            if (s.compareTo("free") == 0)
                pipe("disp:orange" + "\n");
            if (s.compareTo("occupied") == 0)
                pipe("disp:red" + "\n");
        }

        private String getVehicleID() {
            return VehicleID;
        }

        private void setVehicleID(String s) {
            VehicleID = s;
        }

        private double getParkingRate() {
            return ParkingRate;
        }

        private void setParkingRate(double value) {
            ParkingRate = value;
        }
    }

    public static class MultipleAxisJoystickObject extends BaseInstanceEnabler {
        private int XValue;
        private int YValue;
        private int ZValue;
        private boolean digitalInputState;
        private int digitalInputCounter;
        private String applicationType;

        public MultipleAxisJoystickObject() {
            XValue = -100;
            YValue = -100;
            ZValue = -100;
            digitalInputState = true;
            applicationType = "";
            digitalInputCounter = 0;
            // notify new date each 5 second
            // Timer timer = new Timer();
            // timer.schedule(new TimerTask() {
            // @Override
            // public void run() {
            // fireResourcesChange(13);
            // }
            // }, 5000, 5000);
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Device Resource " + resourceid);
            switch (resourceid) {
            case 5500:
                return ReadResponse.success(resourceid, getDigitalInputState());
            case 5501:
                return ReadResponse.success(resourceid, getDigitalInputCounter());
            case 5702:
                return ReadResponse.success(resourceid, getXValue());
            case 5703:
                return ReadResponse.success(resourceid, getYValue());
            case 5704:
                return ReadResponse.success(resourceid, getZValue());
            case 5750:
                return ReadResponse.success(resourceid, getApplicationType());
            default:
                return super.read(resourceid);
            }
        }

        public void updateYaxisChange(String nextMove) {

            if (nextMove.compareTo("-100") == 0) {
                System.out.println("PakringSpot- send joystick event (-100): " + System.currentTimeMillis());
                updateYAxis(-100);
                pipe("disp:green" + "\n");
            }
            if (nextMove.compareTo("100") == 0) {
                updateYAxis(100);
                pipe("disp:red" + "\n");
            }
        }

        private void updateYAxis(int delta) {
            YValue = delta;
            fireResourcesChange(5703);
        }
        /*
         * @Override public ExecuteResponse execute(int resourceid, String params) { System.out.println(
         * "Execute on Device resource " + resourceid); if (params != null && params.length() != 0) System.out.println(
         * "\t params " + params); return ExecuteResponse.success(); }
         */

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on Device Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 5750:
                setApplicationType((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            default:
                return super.write(resourceid, value);
            }
        }

        private int getDigitalInputCounter() {
            return digitalInputCounter;
        }

        private boolean getDigitalInputState() {
            return digitalInputState;
        }

        private int getXValue() {
            return XValue;
        }

        private int getYValue() {
            return YValue;
        }

        private int getZValue() {
            return ZValue;
        }

        private String getApplicationType() {
            return applicationType;
        }

        private void setApplicationType(String s) {
            applicationType = s;
        }
    }

    public static class AddressableTextDisplayObject extends BaseInstanceEnabler {
        private int XCordinate;
        private int YCordinate;
        private int maxXCordinate;
        private int maxYCordinate;

        private float level;
        private float Contrast;
        private String text;
        private String applicationType;

        public AddressableTextDisplayObject() {
            XCordinate = 0;
            YCordinate = 0;
            maxXCordinate = 0;
            maxYCordinate = 0;
            level = 0;
            Contrast = 0;
            applicationType = "";
            text = "green";
            // notify new date each 5 second
            /*
             * Timer timer = new Timer(); timer.schedule(new TimerTask() {
             * 
             * @Override public void run() { fireResourcesChange(13); } }, 5000, 5000);
             */
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Device Resource " + resourceid);
            switch (resourceid) {
            case 5528:
                return ReadResponse.success(resourceid, getXCordinate());
            case 5529:
                return ReadResponse.success(resourceid, getYCordinate());
            case 5545:
                return ReadResponse.success(resourceid, getMaxXCordinate());
            case 5546:
                return ReadResponse.success(resourceid, getMaxYCordinate());
            case 5548:
                return ReadResponse.success(resourceid, getLevel());
            case 5750:
                return ReadResponse.success(resourceid, getApplicationType());
            case 5527:
                return ReadResponse.success(resourceid, getText());
            case 5531:
                return ReadResponse.success(resourceid, getContrast());
            default:
                return super.read(resourceid);
            }
        }

        /*
         * @Override public ExecuteResponse execute(int resourceid, String params) { System.out.println(
         * "Execute on Device resource " + resourceid); if (params != null && params.length() != 0) System.out.println(
         * "\t params " + params); return ExecuteResponse.success(); }
         */

        private float getContrast() {
            return Contrast;
        }

        private String getText() {
            return text;
        }

        private float getLevel() {
            return level;
        }

        private int getMaxYCordinate() {
            return maxYCordinate;
        }

        private int getMaxXCordinate() {
            return maxXCordinate;
        }

        private int getYCordinate() {
            return YCordinate;
        }

        private int getXCordinate() {
            return XCordinate;
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on Device Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 5527:
                System.out.println("ParkingSpot- received text event ( " + (String) value.getValue() + " ): "
                        + System.currentTimeMillis());
                setText((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            default:
                return super.write(resourceid, value);
            }
        }

        private String getApplicationType() {
            return applicationType;
        }

        private void setText(String s) {
            text = s;
            updateModuleStatus(s);
        }
    }

    public static class UpdateObject extends BaseInstanceEnabler {
        private String PackageURI;
        private int State;
        private int UpdateResult;
        private String PkgName;

        public UpdateObject() {
            PackageURI = null;
            State = 0;
            UpdateResult = 0;
            PkgName = "Default";
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Device Resource " + resourceid);
            switch (resourceid) {
            case 3:
                return ReadResponse.success(resourceid, getState());
            case 6:
                return ReadResponse.success(resourceid, getUpdateResult());
            case 7:
                return ReadResponse.success(resourceid, getPkgName());
            default:
                return super.read(resourceid);
            }
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on Device Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 1:
                setPackageURI((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();

            default:
                return super.write(resourceid, value);
            }
        }

        private String getPkgName() {
            return "Leshan Example Device";
        }

        private int getState() {
            return State;
        }

        private int getUpdateResult() {
            return UpdateResult;
        }

        private void setPackageURI(String s) {
            PackageURI = s;
            String data = null;
            FileReader fileReader = null;
            try {
                fileReader = new FileReader(s);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                data = bufferedReader.readLine();
                if (data != null) {
                    pipe(data);
                    UpdateResult = 1;
                    State = 1;
                    PkgName = s;
                } else {
                    UpdateResult = -1;
                    State = -1;
                }
                bufferedReader.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
