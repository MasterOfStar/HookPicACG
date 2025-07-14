package cc.aoeiuv020.hookpicacg;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressWarnings({"RedundantThrows", "unused"})
public class MainHook implements IXposedHookLoadPackage {
    // 1x1 透明 GIF 图片的 Base64 编码
    private static final String TRANSPARENT_GIF_BASE64 = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
    private static final byte[] TRANSPARENT_GIF = Base64.decode(TRANSPARENT_GIF_BASE64, Base64.DEFAULT);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("handleLoadPackage: " + lpparam.processName);
        Class<?> dialogClass = XposedHelpers.findClassIfExists("com.picacomic.fregata.utils.views.AlertDialogCenter", lpparam.classLoader);
        if (dialogClass == null) {
            return;
        }

        // 拦截广告域名请求（WebView）
        hookWebViewClient(lpparam);
        
        // 拦截广告域名请求（OkHttp3）
        hookOkHttp(lpparam);

        // 原始的各种Hook方法保持不变
        XposedHelpers.findAndHookMethod(
                dialogClass,
                "showAnnouncementAlertDialog",
                Context.class,
                String.class,
                String.class,
                String.class,
                String.class,
                View.OnClickListener.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
        
        // ... 保持其他原有的Hook方法不变 ...
        // 注意：这里省略了原有代码中的其他Hook方法，实际代码中需保留
    }

    /**
     * 拦截WebView中的广告请求
     */
    private void hookWebViewClient(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 拦截旧版WebViewClient (String url)
            XposedHelpers.findAndHookMethod(
                    WebViewClient.class,
                    "shouldInterceptRequest",
                    WebView.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String url = (String) param.args[1];
                            if (isAdUrl(url)) {
                                param.setResult(createTransparentGifResponse());
                            }
                        }
                    });

            // 拦截新版WebViewClient (WebResourceRequest) - API 21+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                XposedHelpers.findAndHookMethod(
                        WebViewClient.class,
                        "shouldInterceptRequest",
                        WebView.class,
                        android.webkit.WebResourceRequest.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                Object request = param.args[1];
                                String url = (String) XposedHelpers.callMethod(request, "getUrl");
                                if (isAdUrl(url.toString())) {
                                    param.setResult(createTransparentGifResponse());
                                }
                            }
                        });
            }
        } catch (Throwable t) {
            XposedBridge.log("Hook WebViewClient error: " + t);
        }
    }

    /**
     * 拦截OkHttp网络库中的广告请求
     */
    private void hookOkHttp(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            final Class<?> callClass = XposedHelpers.findClass("okhttp3.Call", lpparam.classLoader);
            final Class<?> requestClass = XposedHelpers.findClass("okhttp3.Request", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    "okhttp3.internal.http.RealInterceptorChain",
                    lpparam.classLoader,
                    "proceed",
                    requestClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object request = param.args[0];
                            String url = (String) XposedHelpers.callMethod(
                                    XposedHelpers.callMethod(request, "url"),
                                    "toString");

                            if (isAdUrl(url)) {
                                // 创建伪造的透明图片响应
                                Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", lpparam.classLoader);
                                Class<?> responseBodyClass = XposedHelpers.findClass("okhttp3.ResponseBody", lpparam.classLoader);
                                Class<?> mediaTypeClass = XposedHelpers.findClass("okhttp3.MediaType", lpparam.classLoader);

                                // 创建MediaType
                                Object mediaType = XposedHelpers.callStaticMethod(
                                        mediaTypeClass,
                                        "parse",
                                        "image/gif");

                                // 创建ResponseBody
                                Object responseBody = XposedHelpers.newInstance(
                                        responseBodyClass,
                                        mediaType,
                                        TRANSPARENT_GIF);

                                // 构建伪造响应
                                Object response = XposedHelpers.newInstance(
                                        responseClass,
                                        request,
                                        200,  // HTTP 200 OK
                                        "OK",
                                        null,  // Handshake
                                        null,  // Headers
                                        responseBody,
                                        null,  // CacheControl
                                        null,  // SentRequestAtMillis
                                        null,  // ReceivedResponseAtMillis
                                        null,  // Exchange
                                        null   // PriorResponse
                                );

                                param.setResult(response);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("Hook OkHttp error: " + t);
        }
    }

    /**
     * 创建透明GIF的WebResourceResponse
     */
    private WebResourceResponse createTransparentGifResponse() {
        InputStream inputStream = new ByteArrayInputStream(TRANSPARENT_GIF);
        return new WebResourceResponse("image/gif", "UTF-8", inputStream);
    }

    /**
     * 判断是否为广告域名
     */
    private boolean isAdUrl(String url) {
        return url != null && url.contains("pica-ad-api.diwodiwo.xyz");
    }
}
