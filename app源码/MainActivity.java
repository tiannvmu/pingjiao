package com.tianvmu.jiaolin;

// ============================================================
//  MainActivity.java
//  杏林一键评教 - 天女目瑛 制作
//  架构：WebView直接操作学校网页，注入JS自动操作
// ============================================================

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.util.Log;

import org.json.JSONObject;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView 网页视图;
    private final Handler 主线程处理器 = new Handler(Looper.getMainLooper());
    private volatile String 学号 = "";
    private volatile String 密码 = "";
    private static final String 登录页地址 = "http://59.46.67.198:60001/tmweb_xl/login.aspx";

    // SharedPreferences用于记住账号密码
    private SharedPreferences 偏好设置;
    private static final String 偏好文件名 = "jiaolin_config";

    @Override
    protected void onCreate(Bundle 保存的状态) {
        super.onCreate(保存的状态);

        偏好设置 = getSharedPreferences(偏好文件名, Context.MODE_PRIVATE);

        网页视图 = new WebView(this);
        setContentView(网页视图);

        WebSettings 设置 = 网页视图.getSettings();
        设置.setJavaScriptEnabled(true);
        设置.setDomStorageEnabled(true);
        设置.setUseWideViewPort(true);
        设置.setLoadWithOverviewMode(true);
        设置.setDefaultTextEncodingName("UTF-8");
        设置.setAllowFileAccess(true);
        设置.setAllowUniversalAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            设置.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(网页视图, true);

        网页视图.addJavascriptInterface(new 安卓桥接(), "AndroidBridge");

        网页视图.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                页面加载完成(view, url);
            }
        });
        网页视图.setWebChromeClient(new WebChromeClient());

        网页视图.loadUrl("file:///android_asset/www/index.html");
    }

    /**
     * 页面加载完成后的回调，根据URL注入不同自动化脚本
     */
    private void 页面加载完成(final WebView view, String url) {
        if (url == null) return;
        String 小写URL = url.toLowerCase(Locale.ROOT);
        Log.d("杏林评教", "页面加载完成: " + url);

        if (小写URL.contains("login.aspx")) {
            // 登录页：防重复注入
            if (view.getTag() == null) {
                view.setTag("injected");
                主线程处理器.postDelayed(new Runnable() {
                    @Override public void run() { 注入自动登录脚本(view); }
                }, 500);
            }
        } else if (小写URL.contains("ischool.aspx")) {
            // 登录成功，跳转评教页
            view.loadUrl("http://59.46.67.198:60001/tmweb_xl/TCStudent/SStudentEvaluateTeacher.aspx");
        } else if (小写URL.contains("declare.aspx")) {
            注入同意声明脚本(view);
        } else if (小写URL.contains("sstudentevaluateteacher.aspx")) {
            // 延迟等页面完全渲染
            主线程处理器.postDelayed(new Runnable() {
                @Override public void run() { 注入自动评教脚本(view); }
            }, 800);
        }
    }

    /**
     * 注入自动登录脚本
     * 自己用JSEncrypt加密密码，绕过checkLogin和验证码
     * 手动添加Button1隐藏字段让ASP.NET识别登录按钮被点击
     */
    private void 注入自动登录脚本(WebView view) {
        String js学号 = JSONObject.quote(学号 == null ? "" : 学号);
        String js密码 = JSONObject.quote(密码 == null ? "" : 密码);

        String 脚本 =
            "(function() {" +
            "  var 输入学号 = " + js学号 + ";" +
            "  var 输入密码 = " + js密码 + ";" +
            "  var 重试次数 = 0;" +
            "  function 尝试() {" +
            "    重试次数++;" +
            "    if (重试次数 > 50) return;" +
            "    var uid = document.getElementById('User_ID');" +
            "    var pass = document.getElementById('User_Pass');" +
            "    if (!uid || !pass) { setTimeout(尝试, 200); return; }" +
            "    if (typeof JSEncrypt === 'undefined') { setTimeout(尝试, 200); return; }" +
            "    if (typeof PUBLIC_KEY === 'undefined') { setTimeout(尝试, 200); return; }" +
            "    if (typeof window.Role === 'undefined') { setTimeout(尝试, 200); return; }" +
            "    uid.value = 输入学号;" +
            "    var encrypt = new JSEncrypt();" +
            "    encrypt.setPublicKey(PUBLIC_KEY);" +
            "    var 密文 = encrypt.encrypt(输入密码);" +
            "    if (!密文 || 密文 === false) { setTimeout(尝试, 200); return; }" +
            "    pass.value = 密文;" +
            "    try { window.Role.SetSelectedIndex(3); } catch(e) {}" +
            "    var roleVI = document.getElementById('cobRole_VI');" +
            "    if (roleVI) roleVI.value = 'bdfeb86e-3c29-4696-a19d-6c428850cea3';" +
            "    var h = document.createElement('input');" +
            "    h.type = 'hidden';" +
            "    h.name = 'Button1';" +
            "    h.value = ' ';" +
            "    document.getElementById('form1').appendChild(h);" +
            "    document.getElementById('form1').submit();" +
            "  }" +
            "  尝试();" +
            "})();";

        执行JS(view, 脚本);
    }

    /**
     * 注入同意声明脚本
     */
    private void 注入同意声明脚本(WebView view) {
        String 脚本 =
            "(function() {" +
            "  var cb = document.getElementById('CBDeclare');" +
            "  if (cb) cb.checked = true;" +
            "  var panel = document.getElementById('BtnOKPanel');" +
            "  if (panel) panel.style.display = 'block';" +
            "  var btn = document.getElementById('BtnOK');" +
            "  if (btn) btn.click();" +
            "})();";
        执行JS(view, 脚本);
    }

    /**
     * 注入自动评教脚本
     *
     * 核心修复：用"课程名"而非"序号"来跟踪进度
     * 这样即使列表长度变化（已评的课从列表消失）也不会越界
     *
     * 流程：init → switching(切换课程) → scoring(打分保存) → saved(检查结果) → switching(下一门)
     */
    private void 注入自动评教脚本(WebView view) {
        String 脚本 =
            "(function() {" +
            // 创建悬浮进度框
            "  if (!document.getElementById('pj_progress')) {" +
            "    var d = document.createElement('div');" +
            "    d.id = 'pj_progress';" +
            "    d.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99999;background:#1a73e8;color:#fff;padding:12px;font-size:15px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,0.3);';" +
            "    document.body.appendChild(d);" +
            "  }" +
            "  var 日志 = document.getElementById('pj_progress');" +
            "  function 显示(msg) { 日志.textContent = msg; }" +
            "" +
            // 读取状态
            "  var 状态 = localStorage.getItem('pj_state') || 'init';" +
            "  var 已完成课程 = localStorage.getItem('pj_done') || '';" +
            "  var 已完成列表 = 已完成课程 ? 已完成课程.split('||') : [];" +
            "  var 当前课程名 = localStorage.getItem('pj_current') || '';" +
            "" +
            // 获取课程列表
            "  var 下拉框 = document.getElementById('dropLesson');" +
            "  if (!下拉框) return;" +
            "" +
            // 找出所有未评且未完成的课程
            "  var 待评课程 = [];" +
            "  for (var i = 0; i < 下拉框.options.length; i++) {" +
            "    var 文本 = 下拉框.options[i].text;" +
            "    if (文本.indexOf('未评') >= 0) {" +
            "      var 课程名 = 文本.split('|')[0].trim();" +
            "      var 教师 = 文本.split('|')[1] ? 文本.split('|')[1].trim() : '';" +
            "      var 标识 = 课程名 + '|' + 教师;" +
            "      if (已完成列表.indexOf(标识) < 0) {" +
            "        待评课程.push({idx: i, val: 下拉框.options[i].value, txt: 文本, 名: 标识});" +
            "      }" +
            "    }" +
            "  }" +
            "" +
            "  if (状态 == 'init') {" +
            "    if (待评课程.length == 0) {" +
            // 没有未评课程了，生成报告
            "      var 所有课程 = [];" +
            "      for (var j = 0; j < 下拉框.options.length; j++) {" +
            "        所有课程.push(下拉框.options[j].text);" +
            "      }" +
            "      localStorage.setItem('pj_report', JSON.stringify(所有课程));" +
            "      localStorage.setItem('pj_state', 'report');" +
            "      window.location.reload();" +
            "      return;" +
            "    }" +
            "    显示('📚 共' + 待评课程.length + '门未评，开始评教...');" +
            "    localStorage.setItem('pj_state', 'switching');" +
            "    状态 = 'switching';" +
            "  }" +
            "" +
            "  if (状态 == 'switching') {" +
            "    if (待评课程.length == 0) {" +
            "      显示('🎉 全部完成！');" +
            "      localStorage.setItem('pj_state', 'report');" +
            "      var 所有课程2 = [];" +
            "      for (var k = 0; k < 下拉框.options.length; k++) {" +
            "        所有课程2.push(下拉框.options[k].text);" +
            "      }" +
            "      localStorage.setItem('pj_report', JSON.stringify(所有课程2));" +
            "      window.location.reload();" +
            "      return;" +
            "    }" +
            "    var c = 待评课程[0];" +
            "    显示('📝 切换课程: ' + c.txt.split('|')[0]);" +
            "    下拉框.selectedIndex = c.idx;" +
            "    localStorage.setItem('pj_current', c.名);" +
            "    localStorage.setItem('pj_state', 'scoring');" +
            "    __doPostBack('dropLesson', '');" +
            "    return;" +
            "  }" +
            "" +
            "  if (状态 == 'scoring') {" +
            "    var radios = document.querySelectorAll('input[type=radio][name*=radioItem]');" +
            "    if (radios.length == 0) {" +
            "      显示('⚠️ 无评分项，跳过');" +
            // 标记为已完成，跳到下一门
            "      已完成列表.push(当前课程名);" +
            "      localStorage.setItem('pj_done', 已完成列表.join('||'));" +
            "      localStorage.setItem('pj_state', 'switching');" +
            "      window.location.reload();" +
            "      return;" +
            "    }" +
            // 选优秀
            "    var groups = {};" +
            "    radios.forEach(function(r) { if (!groups[r.name]) groups[r.name] = []; groups[r.name].push(r); });" +
            "    Object.keys(groups).forEach(function(name) { groups[name][0].checked = true; groups[name][0].click(); });" +
            // 选非常称职
            "    var sat = document.querySelector('input[name=radioSatisfaction][value=非常称职]');" +
            "    if (sat) { sat.checked = true; sat.click(); }" +
            // 填建议
            "    var txt = document.getElementById('txtSuggest');" +
            "    if (txt) txt.value = '老师教学认真负责，讲解清晰，受益匪浅。';" +
            "    var 课程名显示 = 下拉框.options[下拉框.selectedIndex].text.split('|')[0];" +
            "    显示('📝 ' + 课程名显示 + ' - 已选优秀，保存中...');" +
            "    localStorage.setItem('pj_state', 'saved');" +
            "    var saveBtn = document.querySelector('input[name=btnSave]');" +
            "    if (saveBtn) saveBtn.click();" +
            "    else document.getElementById('form1').submit();" +
            "    return;" +
            "  }" +
            "" +
            "  if (状态 == 'saved') {" +
            // 检查当前课程是否变成"已评"
            "    var cur = 下拉框.options[下拉框.selectedIndex];" +
            "    if (cur && cur.text.indexOf('已评') >= 0) {" +
            "      显示('✅ ' + 当前课程名.split('|')[0] + ' 保存成功！');" +
            "    } else {" +
            "      显示('⚠️ ' + 当前课程名.split('|')[0] + ' 保存可能未成功');" +
            "    }" +
            // 标记为已完成
            "    已完成列表.push(当前课程名);" +
            "    localStorage.setItem('pj_done', 已完成列表.join('||'));" +
            "    localStorage.setItem('pj_state', 'switching');" +
            "    localStorage.removeItem('pj_current');" +
            "    setTimeout(function() { window.location.reload(); }, 800);" +
            "    return;" +
            "  }" +
            "" +
            "  if (状态 == 'report') {" +
            // 报告页面：显示所有课程状态
            "    var 报告数据 = localStorage.getItem('pj_report');" +
            "    var 课程列表 = 报告数据 ? JSON.parse(报告数据) : [];" +
            "    var html = '<div style=\"position:fixed;top:0;left:0;right:0;bottom:0;z-index:99999;background:#f0f2f5;overflow-y:auto;padding:20px;\">';" +
            "    html += '<h2 style=\"text-align:center;color:#1a73e8;margin-bottom:20px;\">📊 评教报告</h2>';" +
            "    var 成功数 = 0;" +
            "    var 失败数 = 0;" +
            "    课程列表.forEach(function(c, i) {" +
            "      var 状态色 = c.indexOf('已评') >= 0 ? '#137333' : '#d93025';" +
            "      var 状态文 = c.indexOf('已评') >= 0 ? '✅ 已评' : '❌ 未评';" +
            "      if (c.indexOf('已评') >= 0) 成功数++; else 失败数++;" +
            "      html += '<div style=\"background:#fff;border-radius:8px;padding:12px 16px;margin-bottom:8px;display:flex;justify-content:space-between;align-items:center;\">';" +
            "      html += '<span style=\"font-size:14px;\">' + (i+1) + '. ' + c.split('|')[0] + ' | ' + (c.split('|')[1]||'') + '</span>';" +
            "      html += '<span style=\"font-size:13px;color:' + 状态色 + ';font-weight:bold;\">' + 状态文 + '</span>';" +
            "      html += '</div>';" +
            "    });" +
            "    html += '<div style=\"text-align:center;margin:20px 0;font-size:16px;\">';" +
            "    html += '✅ 已评：' + 成功数 + ' 门 &nbsp; ❌ 未评：' + 失败数 + ' 门';" +
            "    html += '</div>';" +
            "    html += '<button onclick=\"AndroidBridge.返回首页()\" style=\"display:block;margin:20px auto;background:#1a73e8;color:#fff;border:none;padding:14px 40px;font-size:16px;border-radius:25px;\">返回登录界面</button>';" +
            "    html += '<p style=\"text-align:center;color:#999;font-size:12px;margin-top:10px;\">⚠️ 本程序仅供学习交流，请于24小时内删除</p>';" +
            "    html += '</div>';" +
            "    var old = document.getElementById('pj_report_div');" +
            "    if (old) old.remove();" +
            "    var div = document.createElement('div');" +
            "    div.id = 'pj_report_div';" +
            "    div.innerHTML = html;" +
            "    document.body.appendChild(div);" +
            "    localStorage.clear();" +
            "    return;" +
            "  }" +
            "})();";

        执行JS(view, 脚本);
    }

    private void 执行JS(WebView view, String script) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(script, new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    Log.d("杏林评教", "JS结果: " + value);
                }
            });
        } else {
            view.loadUrl("javascript:" + script);
        }
    }

    /**
     * 安卓桥接类
     */
    public class 安卓桥接 {

        /**
         * 从本地UI接收学号密码，开始自动登录
         * 登录前清除Cookie，避免"已有其他账号登录"
         */
        @JavascriptInterface
        public void 开始登录(String 输入学号, String 输入密码) {
            学号 = 输入学号 == null ? "" : 输入学号;
            密码 = 输入密码 == null ? "" : 输入密码;

            // 保存账号密码到SharedPreferences（记住密码功能）
            SharedPreferences.Editor 编辑器 = 偏好设置.edit();
            编辑器.putString("学号", 学号);
            编辑器.putString("密码", 密码);
            编辑器.apply();

            // 清除WebView所有Cookie，避免会话冲突
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();

            // 清除tag，允许重新注入
            网页视图.setTag(null);

            主线程处理器.post(new Runnable() {
                @Override public void run() {
                    网页视图.loadUrl(登录页地址);
                }
            });
        }

        /**
         * 获取保存的学号（记住密码功能）
         */
        @JavascriptInterface
        public String 获取学号() {
            return 偏好设置.getString("学号", "");
        }

        /**
         * 获取保存的密码（记住密码功能）
         */
        @JavascriptInterface
        public String 获取密码() {
            return 偏好设置.getString("密码", "");
        }

        /**
         * 返回首页（报告页面的返回按钮调用）
         * 清除Cookie + 清除localStorage + 加载本地页面
         */
        @JavascriptInterface
        public void 返回首页() {
            // 清除Cookie
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            // 清除tag
            网页视图.setTag(null);
            // 在主线程加载本地页面
            主线程处理器.post(new Runnable() {
                @Override public void run() {
                    网页视图.loadUrl("file:///android_asset/www/index.html");
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (网页视图 != null && 网页视图.canGoBack()) 网页视图.goBack();
        else super.onBackPressed();
    }
}