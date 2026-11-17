package dev.goyo.alertcontrol;

import android.os.Bundle;

interface IReceiverService {
    Bundle inspect(int userId) = 0;
    Bundle change(String packageName, int userId, int state) = 1;
    void destroy() = 16777114;
}
