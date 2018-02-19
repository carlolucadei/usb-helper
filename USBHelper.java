/*
Copyright © 2014 Edwin de Jong. All Rights Reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY [LICENSOR] "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/


import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/*
 * https://stackoverflow.com/questions/1891252/getting-mount-point-when-a-usb-device-is-inserted-mac-os-x-and-linux/26137431#26137431
 */

package com.ldautomation.ksenia.loquendo;

public class USBDevice {
    private String mountPoint;
    private String name;
    private String vendorId;
    private String serialNumber;
    
    USBDevice (String mountPoint, String name) {
        this.mountPoint = mountPoint;
        this.name = name;
        this.vendorId = "";
        this.serialNumber = "";
    }
    
    public String getMountPoint() {
        return this.mountPoint;
    }
    
    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }
    
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
}


public class MacOSUSBHelper {

    private static final String SYSTEM_PROFILER_COMMAND = "/usr/sbin/system_profiler";

    private static final String SPUSB_DATA_TYPE = "SPUSBDataType";

    private interface SpUSBDataTypeIdentifiers {

        String ITEMS = "_items";

        String MEDIA = "Media";

        String VOLUMES = "volumes";

        String VENDOR_ID = "vendor_id";

        String MOUNT_POINT = "mount_point";

        String NAME = "_name";

        String SERIAL_NUMBER = "serial_num";
    }

    public static List<USBDevice> findMountedDevicesOsX() throws Exception, IOException,
            ParseException, ParserConfigurationException, SAXException {
        final Process process = new ProcessBuilder(SYSTEM_PROFILER_COMMAND, SPUSB_DATA_TYPE, "-xml").start();
        return findMountedDevicesInConfiguration(process.getInputStream());
    }

    private static List<USBDevice> findMountedDevicesInConfiguration(final InputStream processInputStream)
            throws Exception, IOException, ParseException, ParserConfigurationException, SAXException {
        // Root is an array, the USB devices are hierarchical in _items (and eg. _items(0)._items)
        final NSArray array = (NSArray) (PropertyListParser.parse(processInputStream));
        final NSDictionary dict = (NSDictionary) array.objectAtIndex(0);
        final NSArray itemsArray = (NSArray) dict.objectForKey(SpUSBDataTypeIdentifiers.ITEMS);
        return recurseUSBDevices(itemsArray, null);
    }

    public static List<USBDevice> recurseUSBDevices(NSArray items, final NSDictionary prevDict) {
        final Builder<USBDevice> builder = ImmutableList.builder();
        for (NSObject item : items.getArray()) {
            builder.addAll(recurseUSBDevice((NSDictionary) item, prevDict));
        }

        return builder.build();
    }

    private static List<USBDevice> recurseUSBDevice(final NSDictionary dict, final NSDictionary prevDict) {
        final Builder<USBDevice> devices = ImmutableList.builder();
        for (final Map.Entry<String, NSObject> entry : dict.getHashMap().entrySet()) {

            if (entry.getKey().equals(SpUSBDataTypeIdentifiers.ITEMS)) {
                // The USB device is a hub
                devices.addAll(recurseUSBDevices((NSArray) entry.getValue(), null));
            }
            if (entry.getKey().equals(SpUSBDataTypeIdentifiers.MEDIA)) {
                // The USB device is a hub
                devices.addAll(recurseUSBDevices((NSArray) entry.getValue(), dict));
            }
            if (entry.getKey().equals(SpUSBDataTypeIdentifiers.VOLUMES)) {
                // This is a mountable device. We need to get the volumes, and for each volume, return it.
                NSDictionary source = prevDict != null ? prevDict : dict;
                List<USBDevice> mountedDeviceOpt = parseVolumes((NSArray) (entry.getValue()));
                for (USBDevice mountedDevice : mountedDeviceOpt) {
                    mountedDevice.setVendorId(((NSString) source.objectForKey(SpUSBDataTypeIdentifiers.VENDOR_ID)).toString());
                    mountedDevice.setSerialNumber(((NSString) source.objectForKey(SpUSBDataTypeIdentifiers.SERIAL_NUMBER)).toString());
                    devices.add(mountedDevice);
                }
            }
        }
        return devices.build();
    }

    private static List<USBDevice> parseVolumes(final NSArray nsArray) {
        final Builder<USBDevice> devices = ImmutableList.builder();
        for (final NSObject item : nsArray.getArray()) {
            devices.add(parseVolume((NSDictionary) item));
        }
        return devices.build();
    }

    private static USBDevice parseVolume(final NSDictionary item) {
        final String mountPoint = ((NSString) item.objectForKey(SpUSBDataTypeIdentifiers.MOUNT_POINT)).toString();
        final String name = ((NSString) item.objectForKey(SpUSBDataTypeIdentifiers.NAME)).toString();
        return new USBDevice(mountPoint, name);
    }
}
