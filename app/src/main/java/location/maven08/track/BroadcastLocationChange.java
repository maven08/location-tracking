package location.maven08.track;

import android.app.IntentService;
import android.content.Intent;
import androidx.annotation.Nullable;

/**
 * Created by maven_08 on 07/08/2021.
 */

class BroadcastLocationChange extends IntentService {

    public BroadcastLocationChange(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

    }
}
