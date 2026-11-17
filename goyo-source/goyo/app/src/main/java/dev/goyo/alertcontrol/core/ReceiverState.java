package dev.goyo.alertcontrol.core;

public final class ReceiverState {
    public final String packageName;
    public final int state;

    public ReceiverState(String packageName, int state) {
        Targets.requirePackage(packageName);
        Targets.requireState(state);
        this.packageName = packageName;
        this.state = state;
    }

    public boolean isDisabled() {
        // DISABLED_UNTIL_USED (4) can become enabled on launch; never call that blocked.
        return state == 2 || state == 3;
    }
}
