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

        // 原始Hook方法保持不变
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
        XposedHelpers.findAndHookMethod(
                "com.picacomic.fregata.utils.views.PopupWebview",
                lpparam.classLoader,
                "init",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("beforeHookedMethod: PopupWebview.init(Context)");
                        param.setResult(null);
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.picacomic.fregata.utils.views.BannerWebview",
                lpparam.classLoader,
                "init",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("beforeHookedMethod: BannerWebview.init(Context)");
                        param.setResult(null);
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.picacomic.fregata.activities.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("afterHookedMethod: MainActivity.onCreate(Bundle)");
                        View[] buttons_tabbar = (View[]) XposedHelpers.getObjectField(param.thisObject, "buttons_tabbar");
                        buttons_tabbar[2].setVisibility(View.GONE);
                        Activity activity = (Activity) param.thisObject;
                        ViewGroup root = (ViewGroup) ((ViewGroup) (activity.findViewById(android.R.id.content))).getChildAt(0);
                        for (int i = root.getChildCount() - 1; i >= 0; i--) {
                            View child = root.getChildAt(i);
                            if (TextUtils.equals("com.picacomic.fregata.utils.views.BannerWebview", child.getClass().getName())
                                    || TextUtils.equals("com.picacomic.fregata.utils.views.PopupWebview", child.getClass().getName())) {
                                root.removeViewAt(i);
                            }
                        }
                    }

                });
        XposedHelpers.findAndHookMethod(
                "com.picacomic.fregata.fragments.HomeFragment",
                lpparam.classLoader,
                "onCreateView",
                LayoutInflater.class,
                ViewGroup.class,
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("afterHookedMethod: HomeFragment.onCreateView");
                        View viewPager_banner = (View) XposedHelpers.getObjectField(param.thisObject, "viewPager_banner");
                        ((View) (viewPager_banner.getParent())).setVisibility(View.GONE);
                        View linearLayout_announcements = (View) XposedHelpers.getObjectField(param.thisObject, "linearLayout_announcements");
                        linearLayout_announcements.setVisibility(View.GONE);
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.picacomic.fregata.adapters.ComicPageRecyclerViewAdapter",
                lpparam.classLoader,
                "onCreateViewHolder",
                ViewGroup.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("afterHookedMethod: ComicPageRecyclerViewAdapter.onCreateViewHolder");
                        Object result = param.getResult();
                        if (!TextUtils.equals(result.getClass().getName(), "com.picacomic.fregata.holders.AdvertisementListViewHolder")) {
                            return;
                        }
                        View webView_ads = (View) XposedHelpers.getObjectField(result, "itemView");
                        webView_ads.setVisibility(View.GONE);
                        Object lp = XposedHelpers.newInstance(XposedHelpers.findClass("android.support.v7.widget.RecyclerView$LayoutParams", lpparam.classLoader), 0, 0);
                        webView_ads.setLayoutParams((ViewGroup.LayoutParams) lp);
                    }
                });
        XposedHelpers.findAndHookMethod("com.picacomic.fregata.adapters.ComicListRecyclerViewAdapter", lpparam.classLoader, "onBindViewHolder", XposedHelpers.findClass("android.support.v7.widget.RecyclerView$ViewHolder", lpparam.classLoader), int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object viewHolder = param.args[0];
                if (viewHolder.getClass().getSimpleName().equals("AdvertisementListViewHolder")) {
                    param.setResult(null);
                    View itemView = (View) XposedHelpers.getObjectField(viewHolder, "itemView");
                    ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) itemView.getLayoutParams();
                    // 完全隐藏会影响分页加载的逻辑，所以保留一点，
                    lp.height = 1;
                    itemView.setLayoutParams(lp);
                }
            }
        });
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
                                if (isAdUrl(url)) {
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
            final Class<?> callClass = XposedHelpers.findClassIfExists("okhttp3.Call", lpparam.classLoader);
            if (callClass == null) return;

            final Class<?> requestClass = XposedHelpers.findClass("okhttp3.Request", lpparam.classLoader);
            final Class<?> interceptorClass = XposedHelpers.findClass("okhttp3.Interceptor", lpparam.classLoader);
            final Class<?> chainClass = XposedHelpers.findClass("okhttp3.Interceptor$Chain", lpparam.classLoader);

            // Hook Interceptor.Chain.proceed()
            XposedHelpers.findAndHookMethod(
                    chainClass,
                    "proceed",
                    requestClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object request = param.args[0];
                            Object url = XposedHelpers.callMethod(request, "url");
                            String urlString = (String) XposedHelpers.callMethod(url, "toString");

                            if (isAdUrl(urlString)) {
                                try {
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
                                    Object response = XposedHelpers.callStaticMethod(
                                            responseClass,
                                            "newBuilder",
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
                                    
                                    // 获取最终响应对象
                                    Object finalResponse = XposedHelpers.callMethod(response, "build");
                                    param.setResult(finalResponse);
                                } catch (Throwable t) {
                                    XposedBridge.log("Error creating fake response: " + t);
                                }
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
