package com.freeportmetrics.nearby.messages;

import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.freeportmetrics.nearby.messages.databinding.ActivityMainBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.EddystoneUid;
import com.google.android.gms.nearby.messages.IBeaconId;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageFilter;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final int SUBSCRIPTION_EXPIRATION_SECONDS = 30;
    private static final int PUBLISH_EXPIRATION_SECONDS = 30;
    private static final String MESSAGE_CONTENT = Build.MANUFACTURER + " " + Build.MODEL;
    private static final String MESSAGE_NAMESPACE = "NAMESPACE";
    private static final String MESSAGE_TYPE = "TYPE";
    private static final Message MESSAGE = new Message(MESSAGE_CONTENT.getBytes(), MESSAGE_NAMESPACE, MESSAGE_TYPE);


    //region Activity Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setVm(nearbyState);
        binding.setAh(nearbyActionHandler);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(connectionCallbacks)
                .enableAutoManage(this, onConnectionFailedListener)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
            googleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            unpublish();
            unsubscribe();
        }
        super.onStop();
    }
    //endregion Activity Lifecycle

    //region Google API Client

    GoogleApiClient googleApiClient;

    private final GoogleApiClient.ConnectionCallbacks connectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(LOG_TAG, "Google API client connected.");
            nearbyState.setSupported(true);
            nearbyState.notifyChange();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.w(LOG_TAG, "Google API client suspended.");
            nearbyState.setSupported(false);
            nearbyState.notifyChange();
        }
    };

    private final GoogleApiClient.OnConnectionFailedListener onConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e(LOG_TAG, "Google API client connection failed");
            nearbyState.setSupported(false);
            nearbyState.notifyChange();
        }
    };
    //endregion Google API Client

    //region Nearby Messages

    private final MessageListener messageListener = new MessageListener() {
        @Override
        public void onFound(Message message) {
            String log = null;
            if (isEddystoneBeacon(message)) {
                EddystoneUid eddystoneUid = EddystoneUid.from(message);
                log = "Found Eddystone UID: " + eddystoneUid;
            } else if (isIBeaconBeacon(message)) {
                IBeaconId iBeaconId = IBeaconId.from(message);
                log = "Found iBeacon Id: " + iBeaconId;
            } else {
                log = "Found message: " + new String(message.getContent());
            }
            Log.d(LOG_TAG, log);
            nearbyState.addLog(log);
            nearbyState.notifyChange();
        }

        @Override
        public void onLost(Message message) {
            String log = null;
            if (isEddystoneBeacon(message)) {
                EddystoneUid eddystoneUid = EddystoneUid.from(message);
                log = "Lost Eddystone UID: " + eddystoneUid;
            } else if (isIBeaconBeacon(message)) {
                IBeaconId iBeaconId = IBeaconId.from(message);
                log = "Found iBeacon Id: " + iBeaconId;
            } else {
                log = "Lost message: " + new String(message.getContent());
            }
            Log.w(LOG_TAG, log);
            nearbyState.addLog(log);
            nearbyState.notifyChange();
        }
    };

    private boolean isEddystoneBeacon(Message message) {
        return Message.MESSAGE_NAMESPACE_RESERVED.equals(message.getNamespace()) &&
                Message.MESSAGE_TYPE_EDDYSTONE_UID.equals(message.getType());
    }

    private boolean isIBeaconBeacon(Message message) {
        return Message.MESSAGE_NAMESPACE_RESERVED.equals(message.getNamespace()) &&
                Message.MESSAGE_TYPE_I_BEACON_ID.equals(message.getType());
    }

    //region Publish

    private final PublishCallback publishCallback = new PublishCallback() {
        @Override
        public void onExpired() {
            Log.w(LOG_TAG, "Publish expired.");
            nearbyState.setPublishing(false);
            nearbyState.notifyChange();
        }
    };

    private final ResultCallback<Status> publishResultCallback = new ResultCallback<Status>() {
        @Override
        public void onResult(@NonNull Status status) {
            if (status.isSuccess()) {
                Log.d(LOG_TAG, "PUBLISH Success");
                nearbyState.setPublishing(true);
            } else {
                Log.e(LOG_TAG, "PUBLISH Error: " + status.getStatusMessage());
                nearbyState.setPublishing(false);
            }
            nearbyState.notifyChange();
        }
    };

    private void publish() {
        Log.i(LOG_TAG, "Publishing message: " + new String(MESSAGE.getContent()));
        Nearby.Messages
                .publish(googleApiClient, MESSAGE, getPublishOptions())
                .setResultCallback(publishResultCallback);
    }

    private void unpublish() {
        if (nearbyState.isPublishing()) {
            Log.i(LOG_TAG, "Unpublishing");
            nearbyState.setPublishing(false);
            nearbyState.notifyChange();
            Nearby.Messages.unpublish(googleApiClient, MESSAGE);
        }
    }

    public PublishOptions getPublishOptions() {
        return new PublishOptions.Builder()
                .setCallback(publishCallback)
                .setStrategy(getPublishStrategy())
                .build();
    }

    public Strategy getPublishStrategy() {
        return new Strategy.Builder()
                .setTtlSeconds(PUBLISH_EXPIRATION_SECONDS)
                .setDistanceType(nearbyState.isEarshotDistance() ? Strategy.DISTANCE_TYPE_EARSHOT : Strategy.DISTANCE_TYPE_DEFAULT)
                .build();
    }

    //endregion Publish

    //region Subscribe

    private final ResultCallback<Status> subscribeResultCallback = new ResultCallback<Status>() {
        @Override
        public void onResult(@NonNull Status status) {
            if (status.isSuccess()) {
                Log.d(LOG_TAG, "SUBSCRIBE Success");
                nearbyState.setSubscribed(true);
            } else {
                Log.e(LOG_TAG, "SUBSCRIBE Error: " + status.getStatusMessage());
                nearbyState.setSubscribed(false);
            }
            nearbyState.notifyChange();
        }
    };

    private SubscribeCallback subscribeCallback = new SubscribeCallback() {
        @Override
        public void onExpired() {
            Log.w(LOG_TAG, "Subscribe expired.");
            nearbyState.setSubscribed(false);
            nearbyState.notifyChange();
        }
    };

    @Nullable
    private MessageFilter messageFilter;

    private void subscribe() {
        Log.i(LOG_TAG, "Subscribing");
        Nearby.Messages
                .subscribe(googleApiClient, messageListener, getSubscribeOptions())
                .setResultCallback(subscribeResultCallback);
    }

    private void unsubscribe() {
        if (nearbyState.isSubscribed()) {
            Log.i(LOG_TAG, "Unsubscribing");
            nearbyState.setSubscribed(false);
            nearbyState.notifyChange();
            Nearby.Messages.unsubscribe(googleApiClient, messageListener)
                    .setResultCallback(subscribeResultCallback);
        }
    }

    private SubscribeOptions getSubscribeOptions() {
        if (messageFilter == null) {
            messageFilter = new MessageFilter.Builder()
                    .includeNamespacedType(MESSAGE_NAMESPACE, MESSAGE_TYPE)
                    .includeEddystoneUids(getString(R.string.secret_eddystone_uid_namespace), getString(R.string.secret_eddystone_uid_instance))
                    .includeIBeaconIds(UUID.fromString(getString(R.string.secret_ibeacon_proximityUuid)), null, null)
                    .build();
        }
        return new SubscribeOptions.Builder()
                .setCallback(subscribeCallback)
                .setStrategy(getSubscribeStrategy())
                .setFilter(messageFilter)
                .build();
    }

    public Strategy getSubscribeStrategy() {
        return new Strategy.Builder()
                .setTtlSeconds(SUBSCRIPTION_EXPIRATION_SECONDS)
                .setDistanceType(nearbyState.isEarshotDistance() ? Strategy.DISTANCE_TYPE_EARSHOT : Strategy.DISTANCE_TYPE_DEFAULT)
                .build();
    }

    //endregion Subscribe

    //endregion Nearby Messages

    //region Databinding

    private final NearbyState nearbyState = new NearbyState();

    private final NearbyActionHandler nearbyActionHandler = new NearbyActionHandler() {
        @Override
        public void publishClicked() {
            if (!nearbyState.isSupported()) {
                return;
            }
            if (nearbyState.isPublishing()) {
                MainActivity.this.unpublish();
            } else {
                MainActivity.this.publish();
            }
        }

        @Override
        public void subscribeClicked() {
            if (!nearbyState.isSupported()) {
                return;
            }
            if (nearbyState.isSubscribed()) {
                MainActivity.this.unsubscribe();
            } else {
                MainActivity.this.subscribe();
            }
        }
    };

    //endregion Databinding
}
