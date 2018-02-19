# usb-helper
A way to get some USB details from Mac OS using the following command:

```
host:user$ /usr/sbin/system_profiler SPUSBDataType -xml
```

In that way you should be able to load the following data:

mountPoint
name
vendorId
serialNumber

The code has been developed from a comment found at [Stackoverflow](https://stackoverflow.com/questions/1891252/getting-mount-point-when-a-usb-device-is-inserted-mac-os-x-and-linux/26137431#26137431) and it's working only on Mac OS

No tests available