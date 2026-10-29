package org.sucareto.androidhidkeyboard;

// No paths, shell commands, or arbitrary file operations cross this interface.
interface IHidService {
    int openDevices();
    int writeReport(int device, in byte[] report);
    void closeDevices();
}
