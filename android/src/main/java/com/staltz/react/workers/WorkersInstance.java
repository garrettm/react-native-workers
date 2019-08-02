package com.staltz.react.workers;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.facebook.infer.annotation.Assertions;
import com.facebook.infer.annotation.ThreadConfined;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager.ReactInstanceEventListener;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactInstanceManagerBuilder;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JSBundleLoader;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.modules.systeminfo.AndroidInfoHelpers;
import com.facebook.react.packagerconnection.PackagerConnectionSettings;
import com.facebook.react.shell.MainReactPackage;
import static com.facebook.infer.annotation.ThreadConfined.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WorkersInstance implements ReactInstanceEventListener, LifecycleEventListener {

  private final Integer key;
  private final ReactApplicationContext parentContext;
  private final ReactPackage[] packages;
  private final String bundleRoot;
  private final String bundleResource;
  private final Integer bundlerPort;
  private Promise startedPromise;

  private ReactNativeHost host;
  private ReactInstanceManager manager;

  public WorkersInstance(
    final Integer key,
    final ReactApplicationContext parentContext,
    final ReactPackage[] packages,
    final String bundleRoot,
    final String bundleResource,
    final Integer bundlerPort,
    final Promise startedPromise
  ) {
    this.key = key;
    this.parentContext = parentContext;
    this.packages = packages;
    this.bundleRoot = bundleRoot;
    this.bundleResource = bundleResource;
    this.bundlerPort = bundlerPort;
    this.startedPromise = startedPromise;

    if (canInitialize() && !isInitialized()) {
      initialize();
    } else {
      tryDelayedInitialize();
    }
  }

  private void tryDelayedInitialize() {
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
      @Override
      public void run() {
        if (isInitialized()) return;
        if (canInitialize() && !isInitialized()) {
          initialize();
        } else {
          tryDelayedInitialize();
        }
      }
    }, 75);
  }

  private void tryDelayedStart() {
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
      @Override
      public void run() {
        if (isInitialized()) {
          start();
          return;
        }
        if (canInitialize() && !isInitialized()) {
          initialize();
          start();
        } else {
          tryDelayedStart();
        }
      }
    }, 75);
  }

  private boolean canInitialize() {
    final Activity activity = parentContext.getCurrentActivity();
    return activity != null;
  }

  private boolean isInitialized() {
    return this.host != null;
  }

  private void initialize() {
    final Activity activity = parentContext.getCurrentActivity();
    final Application application = Assertions.assertNotNull(activity).getApplication();
    final ReactNativeHost parentHost = Assertions.assertNotNull((ReactApplication)application).getReactNativeHost();

    this.host = new ReactNativeHost(application) {
      @Override
      protected String getJSMainModuleName() {
        return bundleRoot;
      }

      @Override
      protected String getBundleAssetName() {
        if (bundleResource == null) {
          return null;
        }

        return String.format("%s.bundle", bundleResource);
      }

      @Override
      public boolean getUseDeveloperSupport() {
        return false;
      }

      @Override
      protected ReactInstanceManager createReactInstanceManager() {
        ReactInstanceManagerBuilder builder = ReactInstanceManager.builder()
                .setApplication(application)
                .setJSMainModulePath(getJSMainModuleName())
                .setUseDeveloperSupport(getUseDeveloperSupport())
                .setRedBoxHandler(getRedBoxHandler())
                .setJavaScriptExecutorFactory(getJavaScriptExecutorFactory())
                .setInitialLifecycleState(LifecycleState.BEFORE_CREATE);

        for (ReactPackage reactPackage : getPackages()) {
          builder.addPackage(reactPackage);
        }

        String jsBundleFile = getJSBundleFile();
        if (jsBundleFile != null) {
          builder.setJSBundleFile(jsBundleFile);
        } else {
          builder.setBundleAssetName(Assertions.assertNotNull(getBundleAssetName()));
        }
        return builder.build();
      }

      @Override
      protected List<ReactPackage> getPackages() {
        final ArrayList<ReactPackage> allPackages = new ArrayList<>(Arrays.asList(packages));
        allPackages.add(0, new WorkersInstancePackage());
        allPackages.add(0, new MainReactPackage());
        return allPackages;
      }
    };
  }

  /**
   * Public interface
   */

  @ThreadConfined(UI)
  public void start() {
    if (!isInitialized()) {
      tryDelayedStart();
      return;
    }
    this.manager = this.host.getReactInstanceManager();
    this.manager.addReactInstanceEventListener(this);

    if (!this.manager.hasStartedCreatingInitialContext()) {
      this.manager.createReactContextInBackground();
    }

    this.parentContext.addLifecycleEventListener(this);
    this.onHostResume();
  }

  @ThreadConfined(UI)
  public void stop() {
    if (this.manager != null) {
      this.parentContext.removeLifecycleEventListener(this);
      this.onHostDestroy();
    }
  }

  public void postMessage(final String message) {
    WritableMap body = Arguments.createMap();
    body.putInt("key", this.key);
    body.putString("message", message);

    final ReactInstanceManager manager = Assertions.assertNotNull(this.manager);

    Assertions
      .assertNotNull(manager.getCurrentReactContext())
      .getNativeModule(WorkersInstanceManager.class)
      .emit("message", body);
  }

  /**
   * Event handlers
   */

  @Override
  public void onHostResume() {
    final ReactInstanceManager manager = Assertions.assertNotNull(this.manager);
    final Activity activity = this.parentContext.getCurrentActivity();

    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        manager.onHostResume(
          activity,
          null // No default back button implementation necessary.
        );
      }
    });
  }

  @Override
  public void onHostPause() {
    final ReactInstanceManager manager = Assertions.assertNotNull(this.manager);
    final Activity activity = this.parentContext.getCurrentActivity();

    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        manager.onHostPause(activity);
      }
    });
  }

  @Override
  @ThreadConfined(UI)
  public void onHostDestroy() {
    final ReactInstanceManager manager = Assertions.assertNotNull(this.manager);
    // Use `destroy` instead of `onHostDestroy` to force the destruction
    // of the underlying JSContext.
    manager.destroy();
  }

  @Override
  public void onReactContextInitialized(ReactContext context) {
    if (this.startedPromise != null) {
      context
          .getNativeModule(WorkersInstanceManager.class)
          .initialize(this.key, this.parentContext, this.startedPromise);
      this.startedPromise = null;
    }
  }

}
