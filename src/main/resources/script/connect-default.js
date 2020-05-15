var $;
var layer;
var table;
var step = 0;
var lastConnData;
var rowData;

window.onload = function () {

    function isReady() {
        if (step < 100 && !window.jsCallJava) {
            step++;
            setTimeout(isReady, 100);
        } else {
            initPage();
        }
    }

    isReady();
};


/**初始化页面信息*/
function initPage() {
    layui.use(['jquery', 'table', 'layer'], function () {
        $ = layui.jquery;
        layer = layui.layer;
        table = layui.table;
        initConnectData();
    });
}

/**初始化连接数据*/
function initConnectData() {
    var callback = function(result) {
        if (result.code != 200)
            return;
        lastConnData = JSON.parse(result.msgs);
        var table = layui.table;

        for (var row=0; row<lastConnData.length; row++) {
            var html = '<i class="layui-icon eichong-icon" title="修改连接参数" onclick="updConnectData(' + row + ')"'+'>&#xe642;</i>';
            html += '<i class="layui-icon eichong-icon" title="检查是否能连上" style="margin-left:5px" onclick="checkConnect(' + row + ')"'+'>&#xe66a;</i>';
            lastConnData[row].op = html;
        }
        table.render({
            id: 'dataList',
            elem: '#dataList',
            height: 'full-70',
            data: lastConnData,
            limit: lastConnData.length,
            cols: [[
                {type: 'checkbox'},
                {field: 'name', title: '名称'},
                {field: 'hosts', title: '主机'},
                {field: 'pass', title: '密码'},
                {field: 'op', title: '操作', width:120}
            ]]
        });
        //监听行双击事件
        table.on('rowDouble(dataList)', function(obj){
            rowData = obj.data;
            toDataPage(true);
        });
    }
    callJava("ConnectController", "queryConnect", callback);
}

/**添加连接数据*/
function addConnectData() {
    layer.open({
        type: 2,
        fixed: true,
        maxmin: false,
        resize: false,
        title: '新增连接',
        area: ['460px', '240px'],
        skin: 'layui-layer-lan',
        content: '../page/connect-save.html'
    });
}

/**测试连接数据*/
function checkConnect(row) {
    rowData = lastConnData[row];
    callJava("ConnectController", "checkConnect", rowData.name, false, function (result) {
        if (result.code == 200)
            layer.msg(result.msgs);
    });
}

/*编辑连接数据*/
function updConnectData(row) {
    rowData = lastConnData[row];
    layer.open({
        type: 2,
        fixed: true,
        maxmin: false,
        resize: false,
        title: '编辑连接',
        skin: 'layui-layer-lan',
        area: ['460px', '240px'],
        content: '../page/connect-edit.html'
    });
}

/**删除连接数据*/
var delConfirmParam;
function delConnectData() {
    var checkStatus = table.checkStatus('dataList');
    if (!checkStatus.data || !checkStatus.data.length) {
        layer.msg('请先勾选第一列选择要操作的行');
        return false;
    }

    var strNames = '';
    for( var j = 0,len=checkStatus.data.length; j < len; j++) {
        if (j != 0)
            strNames += ', ';
        strNames += checkStatus.data[j].name + '(' + checkStatus.data[j].hosts + ')';
    }

    var callback = function () {
        callJava("ConnectController", "deleteConnect", JSON.stringify(checkStatus.data), function (result) {
            if (result.code == 200)
                initConnectData();
        });
    };

    delConfirmParam = {
        id: strNames,
        callback: callback
    }
    layer.open({
        title: '连接删除确认',
        type: 2,
        area: ['340px', '170px'],
        fixed: true,
        maxmin: false,
        resize: true,
        skin: 'layui-layer-lan',
        content: '../page/conn-del-confirm.html'
    });
}
