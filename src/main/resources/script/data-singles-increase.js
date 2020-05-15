var $;
var form;
var layer;

layui.use(['jquery', 'form', 'layer'], function () {
    $ = layui.jquery;
    form = layui.form;
    layer = layui.layer;
    form.render();

    var treeParent = parent.insertToParentNode;
    if (treeParent && treeParent.pattern
        && treeParent.pattern !="*" && treeParent.pattern !="") {
        if (treeParent.pattern != ':vpath-for-page:')
            $("#keys").val(treeParent.pattern + ":");
    } else {
        var rootNode = parent.getTreeParent();
        if (rootNode && rootNode.name) {
            var rootNodeName = rootNode.name;
            var pos = rootNodeName.lastIndexOf('(');
            if (pos > 0) {
                var path = rootNodeName.substring(0, pos);
                $("#keys").val(path + ":");
            }
        }
    }

    form.on('select(type)', function (data) {
        var zsco = $("#zsco");
        var mkey = $("#mkey");
        var mval = $("#mval");
        var vals = $("#vals");
        var zsetData = $("#zset-data");
        var hashData = $("#hash-data");
        var elseData = $("#else-data");
        var index = parent.layer.getFrameIndex(window.name);
        if (data.value === '2') {
            hashData.css('display', 'none');
            elseData.css('display', 'none');
            zsetData.css('display', 'block');
            parent.layer.style(index, {
                height: '435px'
            });
        } else if (data.value === '4') {
            zsetData.css('display', 'none');
            elseData.css('display', 'none');
            hashData.css('display', 'block');
            parent.layer.style(index, {
                height: '435px'
            });
        } else {
            hashData.css('display', 'none');
            zsetData.css('display', 'none');
            elseData.css('display', 'block');
            parent.layer.style(index, {
                height: '380px'
            });
        }
    });

    $("#commitBtn").on("click", function () {
        var type = $("#type").val();
        var time = $("#time").val();

        var key = $("#keys").val();
        if (parent.inputValIsEmpty("键名", key)) {
            return false;
        }

        var scoreOrField;
        var vals;
        if (type == '2') {
            scoreOrField = $("#zsco").val();
            if (parent.inputValIsEmpty("分数", scoreOrField)) {
                return false;
            }
            if (!parent.scoreIsValid(scoreOrField)) {
                return false;
            }
            vals = $("#field").val();
            if (parent.inputValIsEmpty("字段", vals)) {
                return false;
            }
        } else if (type == '4') {
            scoreOrField = $("#mkey").val();
            if (parent.inputValIsEmpty("字段", scoreOrField)) {
                return false;
            }
            vals = $("#mval").val();
            if (parent.inputValIsEmpty("键值", vals)) {
                return false;
            }
        } else {
            vals = $("#vals").val();
            if (parent.inputValIsEmpty("键值", vals)) {
                return false;
            }
        }

        var checkTimeFlag = /^(0|[1-9][0-9]*)$/.test(time);
        if (!time || Number(time) <= 0) {
            time = '0';
        } else if (!checkTimeFlag) {
            layer.msg('存活秒数只能输入整数值');
            return;
        }

        parent.callJava("DataSinglesController", "insertKey", type, key, vals, time, scoreOrField, function (result) {
            var index = parent.layer.getFrameIndex(window.name);
            parent.layer.close(index);
            if (result.code != 200)
                return;
            parent.layer.msg("成功添加了Key: " + key);

            if (parent.nodeSelected && key==parent.nodeSelected.name)
                parent.getKeysInfo();
            else
                parent.refreshParentPath(key);
        });
    });

    //注册监听返回事件
    $("#gobackBtn").on("click", function () {
        var index = parent.layer.getFrameIndex(window.name);
        parent.layer.close(index);
        return false;
    });

});

