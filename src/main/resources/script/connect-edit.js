var $;
var form;
var layer;
var connect;

layui.use(['jquery', 'form', 'layer'], function () {
    $ = layui.jquery;
    form = layui.form;
    layer = layui.layer;

    //渲染表单
    form.render();
    loadPageData();
    //注册测试按钮事件
    $("#testBtn").on("click", function () {
        checkConnect();
    });
    //监听提交
    form.on('submit(saveBtn)', function () {
        saveConnect();
        return false;
    });
    //注册监听事件
    $("#backBtn").on("click", function () {
        //关闭弹出层
        var index = parent.layer.getFrameIndex(window.name);
        parent.layer.close(index);
    });
});

/**加载页面数据*/
function loadPageData() {
    $("#name").val(parent.rowData.name);
    $("#hosts").val(parent.rowData.hosts);
    $("#pass").val(parent.rowData.pass);
}

/**显示测试视图*/
function checkConnect() {
    var data = {
        "name": $("#name").val(),
        "hosts": $("#hosts").val(),
        "pass": $("#pass").val()
    };
     parent.callJava("ConnectController", "checkConnect", JSON.stringify(data), false, function (result) {
        if (result.code == 200)
            layer.msg(result.msgs);
    });
}

/**保存连接信息*/
function saveConnect() {
    if (!parent.rowData)
        return;
    var data = {
        "name": $("#name").val(),
        "hosts": $("#hosts").val(),
        "pass": $("#pass").val()
    };

    parent.callJava("ConnectController", "updateConnect", parent.rowData.name, JSON.stringify(data), function (result) {
        if (result.code == 200) {
            var index = parent.layer.getFrameIndex(window.name);
            parent.layer.close(index);
            parent.initConnectData();
        }
    });
}