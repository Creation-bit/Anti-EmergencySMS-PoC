package dev.goyo.alertcontrol.core;

import java.util.Map;

public interface RestoreStore {
    Map<String, Integer> load() throws Exception;
    void save(Map<String, Integer> states) throws Exception;
}
