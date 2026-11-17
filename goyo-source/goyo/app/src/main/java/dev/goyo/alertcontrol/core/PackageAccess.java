package dev.goyo.alertcontrol.core;

import java.util.List;

public interface PackageAccess {
    List<ReceiverState> inspect() throws Exception;
    void setState(String packageName, int state) throws Exception;
}
