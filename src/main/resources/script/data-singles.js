var $;
var layer;
var step = 0;
var nodeSelected;
var nodeOnExpand;
var nodeRightClicked;
var insertToParentNode;
var lastKeyInfo;
var lastKeyCols;
var lastKeyVals;

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

    // 准备对象
    var oLeftPane = document.getElementById('left-pane');
    var oSplitBar = document.getElementById('split-bar');
    var oRightPane = document.getElementById('right-pane');

    // 常量
    var PaneMinWidth = 10;
    // 保持左右两边Pane与SplitBar之间的距离不变
    var LeftPaneGap = oSplitBar.offsetLeft - (oLeftPane.offsetLeft + oLeftPane.offsetWidth);
    var RightPaneGap = oRightPane.offsetLeft - oSplitBar.offsetLeft;
    // 保持总的宽度不变
    var MainW = oRightPane.offsetLeft + oRightPane.offsetWidth;

    // 准备变值
    var mouseX = 0;
    var splitX = 0;

    var tableCells;
    var valsFilterInput;
    var defaultCursor;

    oSplitBar.onmousedown = function(e) {
        var ev = e || window.event;

        // 获取鼠标按下时光标X的值
        mouseX = ev.clientX;

        // 获取拖拽前的参考点位置信息
        splitX = oSplitBar.offsetLeft;

        // 避免移动太快, 跑到周边区域, 然后光标形状变成其它的了
        defaultCursor = oLeftPane.style.cursor;
        oLeftPane.style.cursor = 'ew-resize';
        oRightPane.style.cursor = 'ew-resize';

        document.onmousemove = function(e) {
            var ev = e || window.event;

            /*  变化的是滚动条的位置, 然后根据滚动条的新位置, 确定左右Pane的位置和宽度  */

            var newMouseX = ev.clientX; // 获取当前鼠标光标X的值
            var offset = newMouseX - mouseX; // 移动距离

            // 先确定拖拽后, SplitBar的位置
            var newSplitX = splitX + offset;
            //拖拽时避免两边的pane太窄
            if (newSplitX < PaneMinWidth) {
                newSplitX = PaneMinWidth;
            } else if (newSplitX > (MainW-PaneMinWidth)) {
                newSplitX = MainW-PaneMinWidth;
            }
            oSplitBar.style.left = newSplitX + 'px';

            // 不管怎么拖动, Pane与SplitBar之间的距离是固定的
            oLeftPane.style.width = (newSplitX - LeftPaneGap - oLeftPane.offsetLeft) + 'px';
            oRightPane.style.left = (newSplitX + RightPaneGap) + 'px';
            // 让RightPane右边对齐, 不要越界
            oRightPane.style.width = 'calc(100% - '+ oRightPane.style.left + ')';
        }

        document.onmouseup = function() {
            document.onmousemove = null;
            document.onmouseup = null;
            oLeftPane.style.cursor = defaultCursor;
            oRightPane.style.cursor = defaultCursor;
            oSplitBar.releaseCapture && oSplitBar.releaseCapture();
        }

        oSplitBar.setCapture && oSplitBar.setCapture();
        return false;
    }

    noKeyInfoToShow();
};


/**初始化页面信息*/
function initPage() {
    layui.use(['layer', 'jquery'], function () {
        $ = layui.jquery;
        layer = layui.layer;
        initDbTree();
    });
}

//切换数据视图
function changeDataView(flag) {
    // console.log("changeDataView flag:" + flag);

    //数据按钮
    var thisBtn = $("#tab-btn" + flag);
    // console.log("changeDataView thisBtn:" + thisBtn);

    var elseBtns = $(".tab-btns").not(thisBtn);
    // console.log("changeDataView elseBtns:" + elseBtns);

    elseBtns.removeClass("vals-active-tab");
    elseBtns.addClass("vals-sink-tab");

    thisBtn.removeClass("vals-sink-tab");
    thisBtn.addClass("vals-active-tab");

    //数据视图
    var thisObj = $("#vals" + flag);
    // console.log("changeDataView thisObj:" + thisObj);

    var elseObjs = $(".vals").not(thisObj);
    // console.log("changeDataView elseObj:" + elseObjs);

    elseObjs.removeClass("key-vals-show");
    elseObjs.addClass("key-vals-hide");

    thisObj.removeClass("key-vals-hide");
    thisObj.addClass("key-vals-show");

    if (flag == 2) { // 根据输入动态显示
        // console.log("changeDataView jsonStr:" + $("#currVal").attr("jsonStr"));

        if ($("#currVal").attr("jsonStr") == "true") {

            // console.log("jsonStr=true");

            var options = {
                collapsed: false,
                withQuotes: true
            };

            try {
                // console.log("currVal:" + $("#currVal").val());
                thisObj.jsonViewer(eval('(' + $("#currVal").val() + ')'), options);
            } catch (error) {
                thisObj.jsonViewer(error.message, options);
                // console.log("error:" + error.message);
            }
        }
    }
}

//树配置
var zTreeSetting = {
    check: {
        enable: false
    },
    data: {
        keep: {
            parent: true
        },
        simpleData: {
            enable: true
        }
    },
    view: {
        //showLine: false,
        showIcon: true,
        dbIcon: "../image/database.svg",
        keyIcon: "../image/key.svg",
        openIcon: "../image/fold_open.svg",
        closeIcon: "../image/fold.svg",
        expandIcon: "../image/down.png",
        collapseIcon: "../image/left.png"
        // addDiyDom: showPageView
    },
    callback: {
        onClick: ztreeOnClick,
        onExpand: ztreeOnExpand,
        // onDblClick: ztreeOnDblClick,
        onRightClick: ztreeOnRightClick
    }
};

//树节点点击事件
function ztreeOnClick(event, treeId, treeNode) {
    if(treeNode) {
        nodeSelected = treeNode;
        if (treeNode.isParent) {
            noKeyInfoToShow();
        } else {
            getKeysInfo();
        }
    }
}

//树节点展开事件
function ztreeOnExpand(event, treeId, treeNode) {
    if (null != treeNode && treeNode.isParent) {
        //去除过滤条件
        // $("#key-filter-input").val("");
        nodeOnExpand = treeNode;

        // 如果tree节点携带子节点, 则不再请求后台获取
        if (!treeNode.children || treeNode.children.length == 0)
            loadDbData(treeNode);
        else if (treeNode.children.length < 30) {
            // 为快速返回, Server返回的Key以 [...]表示省略掉了此子目录下的Keys, 需再次刷新子目录
            for (var i = 0, l = treeNode.children.length; i < l; i++) {
                if (treeNode.children[i] && treeNode.children[i].name.indexOf(": [...]") == (treeNode.children[i].name.length-7)) {
                    loadDbData(treeNode);
                    break;
                }
            }
        }
    }
}

//树右击事件
function ztreeOnRightClick(event, treeId, treeNode) {
    if (treeNode) {
        nodeRightClicked = treeNode;
        if (treeNode.isParent) {
            showZtreeMenu(event.clientX, event.clientY);
        } else {
            showZkeyMenu(event.clientX, event.clientY);
        }
        $("body").bind("mousedown", onBodyMouseDown);
    }
}


//隐藏菜单鼠标监听事件
function onBodyMouseDown(event) {
    if (!(event.target.id === "ztree-menu" || $(event.target).parents("#ztree-menu").length > 0)) {
        hideZtreeMenu();
    }
    if (!(event.target.id === "zkey-menu" || $(event.target).parents("#zkey-menu").length > 0)) {
        hideZkeyMenu();
    }
}


//显示菜单
function showZtreeMenu(x, y) {
    $("#ztree-menu ul").show();
    y += document.body.scrollTop;
    x += document.body.scrollLeft;
    $("#ztree-menu").css({"top": y + "px", "left": x + "px", "visibility": "visible"});
}
function showZkeyMenu(x, y) {
    $("#zkey-menu ul").show();
    y += document.body.scrollTop;
    x += document.body.scrollLeft;
    $("#zkey-menu").css({"top": y + "px", "left": x + "px", "visibility": "visible"});
}

//隐藏菜单
function hideZtreeMenu() {
    $("#ztree-menu").css({"visibility": "hidden"});
}
function hideZkeyMenu() {
    $("#zkey-menu").css({"visibility": "hidden"});
}

function refreshTree() {
    if (!nodeRightClicked) {
        layer.msg("请选择一个要操作的对象");
        return false;
    }
    hideZtreeMenu();
    loadDbData(nodeRightClicked);
}

//初始化库
function initDbTree() {
    callJava("DataSinglesController", "treeInit", "", function (result) {
        if(result.code != 200)
            return;

        $("#db-tree").append('<ul id="keyTree" class="ztree"></ul>');
        // console.log("initDbTree id:" + result.data.id + " pattern:" + result.data.pattern);
        $.fn.zTree.init($("#keyTree"), zTreeSetting, result.data);

        var rootNode = getTreeParent(null);
        if (rootNode) {
            loadDbData(rootNode);
        }
    });
}

//加载数据
function loadDbData(node, newKey) {
    // console.log((new Date()).toLocaleTimeString() + " loadDbData:" + node.name + " " + node.pattern);
    callJava("DataSinglesController", "treeData", node.id, node.name, node.pattern, function (result) {
        if (result.code == 200) {
            var zTree = $.fn.zTree.getZTreeObj('keyTree');
            // console.log((new Date()).toLocaleTimeString() + " to removeChildNodes:" + node.name + " " + node.pattern);
            zTree.removeChildNodes(node);
            // console.log((new Date()).toLocaleTimeString() + " to addNodes:" + result.data.length);
            if (result.data.length > 0) {
                // if (result.data.length > 1000) {
                //     var vArray = [result.data[0], result.data[1], result.data[2]];
                //     zTree.addNodes(node, 0, vArray);
                // } else {
                zTree.addNodes(node, 0, result.data);
                // }
                // for (var i = 0, l = result.data.length; i < l; i++) {
                    // console.log("loadDbData(" + i + "):" + result.data[i].name + " " + result.data[i].pattern);
                // }
                if (newKey) {
                    console.log((new Date()).toLocaleTimeString() + " to getNodesByFilter newKey:" + newKey);
                    var newNode = zTree.getNodesByFilter(
                        function (node_i, param) {
                            // if (!node_i.isParent) console.log("node.name:" + node_i.name + " param:" + param);
                            if (!node_i.isParent && node_i.name == param) {
                                return true;
                            }
                            return false;
                        },
                        true, node, newKey);

                    // console.log("treeParent:" + node + " node.name" + node.name + " newNode:" + newNode
                    //     + " newNode.name:" + (newNode ? newNode.name : 'null') + ' newKey:' + newKey);
                    if (newNode) {
                        nodeSelected = newNode;
                        getKeysInfo();
                    }
                }
            }
        }
        // console.log((new Date()).toLocaleTimeString() + " loadDbData finished");
    });
}

function translateType(en) {
    switch (en[0]) {
        case 's':
            if (en == 'string')
                return '字符串';
            break;
        case 'h':
            if (en == 'hash')
                return '哈希表';
            break;
        case 'l':
            if (en == 'list')
                return '列表';
            break;
        case 's':
            if (en == 'set')
                return '集合';
            break;
        case 'z':
            if (en == 'zset')
                return '有序集合';
            break;
    }
    return en;
}

//获取key信息
function getKeysInfo() {
    if (!nodeSelected || nodeSelected.isParent) {
        layer.msg("请选择要操作的KEY");
        return false;
    }
    callJava("DataSinglesController", "keysData", nodeSelected.name, show_key_vals);
}

// 有了它, 就可通过浏览器显示网页各部件样式, 然后通过浏览器的开发模式动态调整样式
function show_test_vals() {
    // layer.open({
    //     title: 'test',
    //     content: 'zdytestlkdjfeiojnp9hq0j]kdoijfu92hudjnksfhq[0ionhf[q0pienof[q0pi3fjh[0qiornjdkdjfiq111'
    // });
    openDelConfirmLayer('KEY的行', "1, 2, 3", null);
    var keyInfo_hash = {
        type : "hash",
        key : "test-hash",
        size : 100,
        ttl : 100,
        text : "test_txt",
        json: [
            {field : "test-field1", value : "test-value"}
            ,{field : "test-field2", value : "test-value"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "test-value!@#$%^&*()_+{L:NOY u`io<.,.,ejp9uw[`"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfio如分数qwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfigoqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"},{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqw让我听enjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-得分", value : "Disconnected from the target VM, address: '127.0.0.1:52291', transport: 'socket'"}
            ,{field : "test-field2", value : "test-value"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "test-value!@#$%^&*()_+{L:NOY u`io<.,.,ejp9uw[`"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfio如分数qwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfigoqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"},{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqw让我听enjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-fieldjdkfioqwenjdsoioieiee", value : "中文只测试快滴 Joe 哦 if 脑额话费看 i 饿哦嗯"}
            ,{field : "test-得分", value : "Disconnected from the target VM, address: '127.0.0.1:52291', transport: 'socket'"}
            ]
    }
    keyInfo_hash.json = JSON.stringify(keyInfo_hash.json);

    var keyInfo_str = {
        type: "string",
        key: "test-string",
        size: 100,
        ttl: 100,
        text: "jdjfioejan;oudnf'pi"
    }
    var result = {
        code : 200,
        data : keyInfo_hash
    };
    show_key_vals(result);
}

var valsLimitLines = 14;
function show_key_vals(result) {
    // console.log("show_key_vals result.code:" + result.code);

    if (result.code != 200)
        return;

    var keyInfo = result.data;
    lastKeyInfo = keyInfo;
    layui.use('table', function() {
        var table = layui.table;
        table.render({
            id:'key-info-table'
            ,elem: '#key-info-table'
            ,cols: [[ //标题栏
                {field: 'key', title: 'KEY'}
                ,{field: 'type', title: '类型', width: 130}
                ,{field: 'size', title: '大小', width: 130}
                ,{field: 'ttl', title: 'TTL', width: 130}
            ]]
            ,data: [{
                "key": keyInfo.key
                ,"type": keyInfo.type
                ,"size": keyInfo.size
                ,"ttl": keyInfo.ttl
            }]
        });
    });

    $("#vals1").html(keyInfo.text);

    var options = {
        collapsed: false,
        withQuotes: true
    };
    var jsonStr = false;
    if (keyInfo.json && keyInfo.json.length > 0) {
        try {
            $('#vals2').jsonViewer(eval('(' + keyInfo.json + ')'), options);
            jsonStr = true;
        } catch (error) {
            $('#vals2').jsonViewer(error.message, options);
        }
    } else {
        $('#vals2').jsonViewer(keyInfo.text, options);
    }

    if (keyInfo.type === 'string') {
        var html = '<textarea id="currVal" class="layui-textarea key-vals-text" jsonStr="'+ jsonStr +'">' + keyInfo.text + '</textarea>';
        html += '<button class="layui-btn eichong-btn-small vals-commit-btn" onclick="updateStr()">';
        html += '<i class="layui-icon">&#x1005;</i>提交</button>';
        $("#vals4").html(html);
    } else {
        var colArr;
        var type;//1:set,2:zset,3:list,4:hash
        var subItemName;
        switch (keyInfo.type) {
            case "set":
                type = 1;
                subItemName = '成员';
                colArr = [
                    {type: 'checkbox'}
                    ,{field: 'row', title: '行', width:60, sort:true}
                    ,{field: 'field', title: subItemName, sort:true}
                    ,{field: 'op', title: '操作', width:120}
                ];
                break;
            case "zset":
                type = 2;
                subItemName = '成员';
                colArr = [
                    {type: 'checkbox'}
                    ,{field: 'row', title: '行', width:60, sort:true}
                    ,{field: 'score', title: '分数', width:100, sort:true}
                    ,{field: 'member', title: subItemName}
                    ,{field: 'op', title: '操作', width:120}
                ];
                break;
            case "list":
                type = 3;
                subItemName = '元素';
                colArr = [
                    {type: 'checkbox'}
                    ,{field: 'row', title: '行', width:60, sort:true}
                    ,{field: 'field', title: subItemName}
                    ,{field: 'op', title: '操作', width:120}
                ];
                break;
            case "hash":
                type = 4;
                subItemName = '字段';
                colArr = [
                    {type: 'checkbox'}
                    ,{field: 'row', title: '行', width:60, sort:true}
                    ,{field: 'field', title: subItemName, width:300, sort:true}
                    ,{field: 'value', title: '值'}
                    ,{field: 'op', title: '操作', width:120}
                ];
                break;
        }

        var capitalizedType = keyInfo.type.substring(0,1).toUpperCase() + keyInfo.type.substring(1);
        var insertFunc = 'insert' + capitalizedType;
        var updateFunc = 'update' + capitalizedType;

        var title1 = '添加新' + subItemName;
        var title2 = '删除勾选' + subItemName;
        var html = '<div style="height:44px; min-height:44px; display:flex; align-items:center">' +
            '<input type="text" id="vals-filter" class="vals-filter-input" ' +
            ' autocomplete="off" style="order:0" oninput="vals_filter()" placeholder="输入'+
            subItemName+'中关键字定位到相应行">';
        html += '<i class="layui-icon eichong-icon" title="'+title1+'" style="order:1;margin-left:10px" onclick="'+insertFunc+'()">&#xe61f;</i>';
        html += '<i class="layui-icon eichong-icon" title="'+title2+'" style="order:2;margin-left:5px" onclick="deleteVals('+type+')">&#xe640;</i></div>';
        html += '<div class="data-table-box"><table id="key-vals-table" class="layui-table"></table></div>';
        $("#vals4").html(html);

        var title3 = '修改本行' + subItemName;
        lastKeyCols = colArr;
        lastKeyVals = JSON.parse(keyInfo.json);
        var html;
        for (var row=0; row<lastKeyVals.length; row++) {
            html = '<i class="layui-icon eichong-icon" title="'+title3+'" style="margin-left:1px" onclick="'+updateFunc+'(' + row + ')"'+'>&#xe642;</i>';
            lastKeyVals[row].row = (row+1);
            lastKeyVals[row].op = html;
        }

        var table = layui.table;
        table.render({
            id: 'key-vals-table'
            ,elem: '#key-vals-table'
            ,page: (lastKeyVals.length>valsLimitLines?true:false)
            ,size: 'sm' //小尺寸的表格
            ,limit: valsLimitLines
            ,cols: [lastKeyCols]
            ,data: lastKeyVals
        });
    }
}

function vals_filter() {
    if (!lastKeyInfo || !lastKeyCols || !lastKeyVals)
        return;

    var val = $("#vals-filter").val();
    var result;
    if (!val || val.trim().length == 0) {
        result = lastKeyVals;
    } else {
        // 循环里面, 为提高效率避免String比较
        var nameIsMember = false;
        if (lastKeyInfo.type == 'zset')
            nameIsMember = true;
        var f = item => {
            if (nameIsMember) {
                if (item.member.indexOf(val) < 0) {
                    return false;
                }
            } else if (item.field.indexOf(val) < 0) {
                return false;
            }
            return true;
        }
        result = lastKeyVals.filter(f);
    }

    var table = layui.table;
    table.render({
        id: 'key-vals-table'
        ,elem: '#key-vals-table'
        ,page: (result.length>valsLimitLines?true:false)
        ,size: 'sm' //小尺寸的表格
        ,limit: valsLimitLines
        ,cols: [lastKeyCols]
        ,data: result
    });
}


//回车查询事件
function keydownLoadTree() {
    if (event.keyCode === 13) {
        loadFilterTree();
    }
}

//模糊匹配
function loadFilterTree() {
    var pattern = $("#key-filter-input").val();
    callJava("DataSinglesController", "treeInit", pattern, function (result) {
        if (result.code == 200) {
            $.fn.zTree.init($("#keyTree"), zTreeSetting, result.data);
            var rootNode = getTreeParent(null);
            if (rootNode) {
                loadDbData(rootNode);
            }
        }
    });
}

//重命名key
function renameKey() {
    if (!nodeSelected || nodeSelected.isParent) {
        layer.msg("请选择要操作的KEY");
        return false;
    }
    renameKey_i(nodeSelected);
}
function renameKeyRc() {
    hideZkeyMenu();
    if (!nodeRightClicked || nodeRightClicked.isParent) {
        return false;
    }
    renameKey_i(nodeRightClicked);
}

function openOneInputLayer(winTitle, txtTitle, defaultVal, txtArea, func) {
    keyModifyParam = {
        lbl: [txtTitle, txtTitle],
        val: [defaultVal, defaultVal],
        disable1: false,
        txtArea: txtArea?'1':'0',
        callback: function (index, val1, val2) {
            func(index, txtArea?val2:val1);
        }
    }
    var h = 280 - (txtArea?60:120);
    layer.open({
        title: winTitle,
        type: 2,
        area: ['430px', ''+h+'px'],
        fixed: true,
        maxmin: false,
        resize: false,
        skin: 'layui-layer-lan',
        content: '../page/key-modify.html'
    });
}

var keyModifyParam;
function openTwoInputLayer(winTitle,title1,title2,default1,default2,disabled1,func) {
    keyModifyParam = {
        lbl: [title1, title2],
        val: [default1, default2],
        disable1: disabled1,
        callback: func
    }
    layer.open({
        title: winTitle,
        type: 2,
        area: ['430px', '280px'],
        fixed: true,
        maxmin: false,
        resize: false,
        skin: 'layui-layer-lan',
        content: '../page/key-modify.html'
    });
}

var delConfirmParam;
function openDelConfirmLayer(strType, id, func) {
    delConfirmParam = {
        strType: strType,
        id: id,
        callback: func
    }
    layer.open({
        title: strType+'删除确认',
        type: 2,
        area: ['340px', '200px'],
        fixed: true,
        maxmin: false,
        resize: true,
        skin: 'layui-layer-lan',
        content: '../page/del-confirm.html'
    });
}

function inputValIsEmpty(name, val) {
    if (!val || val === '' || val.trim() === '') {
        layer.msg(name+"不能为空");
        //console.log("inputValIsEmpty false, name:" + name + "val:" + val)
        return true;
    }
    //console.log("inputValIsEmpty true, name:" + name + "val:" + val)
    return false;
}

function scoreIsValid(score) {
    var l = score.length;
    var c;
    for (var i=0; i<l; i++) {
        c = score.charAt(i);
        if (i == 0 && c == '-')
            continue;
        if (c < '0' || c > '9') {
            layer.msg('分数只能输入整数值');
            return false;
        }
    }
    return true;
}

function renameKey_i(node) {

    var oldKey = node.name;
    var callback = function (index, val1, val2) {
        var oldNode = node;
        var newKey =  val2;
        if (inputValIsEmpty('KEY', newKey)) {
            return;
        }
        layer.close(index);

        callJava("DataSinglesController", "renameKey", oldKey, newKey, function (result) {
            if (result.code == 409) { // Conflict
                layer.msg("KEY: "+newKey+"已存在");
            } else if (result.code == 200) {
                layer.msg("KEY已被重命名为: " + newKey);
                // 从树上移走之前的Key
                removeNodeFromTree(oldNode);
            }
            if (result.code == 409 || result.code == 200) {
                // 刷新新Key所在的树
                var treeParent = getTreeParent(newKey);
                if (treeParent) {
                    loadDbData(treeParent, newKey);
                }
            }
        });
    };
    openTwoInputLayer('KEY重命名','旧KEY','新KEY', oldKey, oldKey, true, callback);
}

function removeNodeFromTree(node) {
    if (!node) return;
    var zTreeObj = $.fn.zTree.getZTreeObj("keyTree");
    zTreeObj.removeNode(node);
    if (node == nodeSelected) {
        nodeSelected = null;
        noKeyInfoToShow();
    }
    if (node == nodeRightClicked) {
        nodeRightClicked = null;
    }
}

//设置key的失效时间
function retimeKey() {
    if (!nodeSelected || nodeSelected.isParent || !lastKeyInfo) {
        layer.msg("请选择要操作的KEY");
        return false;
    }

    var key = nodeSelected.name;
    var ttl = lastKeyInfo.ttl;
    var callback = function (index, text) {
        if (!text || Number(text) <= 0) {
            layer.msg('请输入大于0的整数');
            return;
        }
        var checkFlag = /^(0|[1-9][0-9]*)$/.test(text);
        if (!checkFlag) {
            layer.msg('只能输入整数值');
            return;
        }

        layer.close(index);
        callJava("DataSinglesController", "sendCommandLine",
            "EXPIRE", key, text,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    layer.msg(result.msgs);
                }
            });
    };
    openOneInputLayer('修改KEY存活秒数', 'TTL', ttl, false, callback);
}

function noKeyInfoToShow() {
    $("#vals1").html("");
    $('#vals2').html("");
    $("#vals4").html("");

    layui.use('table', function() {
        var table = layui.table;
        table.render( {
            elem: '#key-info-table'
            ,cellMinWidth: 80 //全局定义常规单元格的最小宽度, layui 2.2.1 新增
            ,cols: [[ //标题栏
                {field: 'key', title: 'KEY'}
                ,{field: 'type', title: '类型', width: 130}
                ,{field: 'size', title: '大小', width: 130}
                ,{field: 'ttl', title: 'TTL', width: 130}
            ]]
            ,data: [{
                "key": ""
                ,"type": ""
                ,"size": ""
                ,"ttl": ""
            }]
        });
    });
}

function copyKeyInfo() {
    if (!nodeSelected || nodeSelected.isParent || !lastKeyInfo) {
        layer.msg("请选择要操作的KEY");
        return false;
    }

    var str = "Key:\n" + lastKeyInfo.key;
    str += "\n\nType:\n" + lastKeyInfo.type;
    str += "\n\nTTL:\n" + lastKeyInfo.ttl;
    str += "\n\nSize:\n" + lastKeyInfo.size;
    str += "\n\nValue:\n" + lastKeyInfo.text;
    copyText(str);
    layer.msg("已复制到剪贴板");
}

function copyText(text) {
    var textarea = document.createElement("textarea");
    var currentFocus = document.activeElement;
    document.body.appendChild(textarea);
    textarea.value = text;
    textarea.focus();
    if (textarea.setSelectionRange){textarea.setSelectionRange(0, textarea.value.length);}
    else {textarea.select();}
    try {var state = document.execCommand("copy");}
    catch(err){var state = false;}
    document.body.removeChild(textarea);
    currentFocus.focus();
    return state;
}

//删除key
function deleteKey() {
    if (nodeSelected && nodeSelected.isParent) {
        deletePath_i(nodeSelected);
        return;
    }

    if (!nodeSelected || nodeSelected.isParent) {
        layer.msg("请选择要操作的KEY");
        return false;
    }
    deleteKey_i(nodeSelected);
}
function deleteKeyRc() {
    hideZkeyMenu();
    if (!nodeRightClicked || nodeRightClicked.isParent) {
        return false;
    }
    deleteKey_i(nodeRightClicked);
}
function deleteKey_i(node) {
    var key = node.name;
    var callback = function () {
        callJava("DataSinglesController", "sendCommandLine",
            "DEL", key,
            function (result) {
                if (result.code === 200) {
                    removeNodeFromTree(node);
                    layer.msg(result.msgs);
                }
            });
    };
    openDelConfirmLayer('KEY', key, callback);
}

//重新加载key
function reloadKey() {
    if (!nodeSelected) {
        layer.msg("请选择要操作的对象");
        return false;
    }
    if (nodeSelected.isParent) {
        loadDbData(nodeSelected);
    } else {
        getKeysInfo();
    }
}

function refreshParentPath(key) {
    var treeParent = getTreeParent(key);
    if (treeParent) {
        loadDbData(treeParent);
    } else {
        var pattern = $("#key-filter-input").val();
        if (pattern != null && pattern != "" && pattern != " ") {
            loadFilterTree();
        } else {
            initDbTree();
        }
    }
}

function deletePathRc() {
    hideZtreeMenu();
    if (!nodeRightClicked || !nodeRightClicked.isParent || !nodeRightClicked.pattern) {
        return false;
    }
    deletePath_i(nodeRightClicked)
}
function deletePath_i(node) {

    var pattern = node.pattern;
    if (pattern == ":vpath-for-page:") {
        layer.alert("虚拟目录仅用于分页显示，可删除父目录，</br>也可输入Key的头部搜索后，再清空根目录", {
            skin: 'layui-layer-lan',
            title: "操作提示"
        });
        return false;
    }

    if (node.id == 'root') {
        if (!pattern || pattern == "*") {
            layer.alert("清空全部数据是高危操作, 建议通过如下步骤做: </br>1) 运维人员停Server</br>2) 将data/aof文件重命名</br>3) 重启Server", {
                skin: 'layui-layer-lan',
                title: "操作提示"
            });
            return false;
        }

        var callback = function () {
            callJava("DataSinglesController", "delFilteredTree", node.name, pattern, function (result) {
                if (result.code == 200) {
                    refreshParentPath(pattern);
                }
            });
        };

        openDelConfirmLayer('树', pattern + '下所有KEY', callback);
        return;
    }

    var callback = function () {
        callJava("DataSinglesController", "sendCommandLine",
            "DELPATH", pattern, function (result) {
            if (result.code == 200) {
                refreshParentPath(pattern);
            }
        });
    };
    openDelConfirmLayer('目录', pattern + '下所有KEY', callback);
}

//添加新KEY
function insertKeyRc() {
    hideZtreeMenu();
    hideZkeyMenu();
    insertToParentNode = nodeRightClicked;
    insertKey_i();
}

function insertKey() {
    if (nodeSelected) {
        if (nodeSelected.isParent)
            insertToParentNode = nodeSelected;
        else
            insertToParentNode = nodeSelected.getParentNode();
    } else {
        insertToParentNode = nodeOnExpand;
    }
    insertKey_i();
}
function insertKey_i() {
    layer.open({
        title: '新增数据',
        type: 2,
        area: ['460px', '380px'],
        fixed: true,
        maxmin: false,
        resize: false,
        skin: 'layui-layer-lan',
        content: '../page/data-singles-increase.html'
    });
}

//修改string
function updateStr() {
    if (!nodeSelected || nodeSelected.isParent) {
        layer.msg("请选择要操作的KEY");
        return false;
    }

    var str = $("#currVal").val();
    if ($("#currVal").attr("jsonStr") == "true") {
        if (isJSON(str) == false) {
            layer.msg("修改失败!不再是JSON格式");
            return;
        }
    }

    callJava("DataSinglesController", "sendCommandLine",
        "SET", nodeSelected.name, str,
        function (result) {
            if (result.code === 200) {
                getKeysInfo();
                layer.msg(result.msgs);
            }
    });
}


/**----------------------set start----------------------*/
//新增set的item
function insertSet() {
    if (!nodeSelected || nodeSelected.isParent) {
        layer.msg("请选择要操作的KEY");
        return false;
    }

    var key = nodeSelected.name;
    var callback = function (index, text) {
        if (inputValIsEmpty('成员值', text)) {
            return;
        }
        layer.close(index);
        callJava("DataSinglesController", "sendCommandLine",
            "SADD", key, text,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    layer.msg(result.msgs);
                }
            });
    };
    openOneInputLayer('集合添加新成员', '成员值', '', true, callback);
}

//修改set的item
function updateSet(row) {
    if (!lastKeyInfo) {
        layer.msg("请选择要操作的KEY");
        return false;
    }
    if (row < 0 || !lastKeyVals || row >= lastKeyVals.length) {
        layer.msg("请选择要操作的行");
        return false;
    }
    var key = lastKeyInfo.key;
    var val = lastKeyVals[row].field;
    var callback = function (index, text) {
        if (inputValIsEmpty('成员值', text)) {
            return;
        }
        layer.close(index);
        callJava("DataSinglesController", "updateVal",
            1, key, val, null, text,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    layer.msg(result.msgs);
                }
            });
    };
    openOneInputLayer('修改第'+(row+1)+'行集合成员', '成员值', val, true, callback);
}


/**----------------------zset start----------------------*/
//新增zset的item
function insertZset() {
    if (!nodeSelected || nodeSelected.isParent) {
        layer.msg("请选择要操作的KEY");
        return false;
    }

    var key = nodeSelected.name;
    var callback = function (index, val1, val2) {
        var score = val1;
        var member = val2;
        if (inputValIsEmpty('分数', score)) {
            return;
        }
        if (inputValIsEmpty('成员', member)) {
            return;
        }

        if (!scoreIsValid(score)) {
            return;
        }
        layer.close(index);

        callJava("DataSinglesController", "sendCommandLine",
            "ZADD", key, score, member,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    // layer.msg(result.msgs);
                }
            });
    };
    openTwoInputLayer('有序集合添加新成员','分数','成员', '', '', false, callback);
}

//修改zset的item
function updateZset(row) {
    if (!lastKeyInfo) {
        layer.msg("请选择要操作的KEY");
        return false;
    }
    if (row < 0 || !lastKeyVals || row >= lastKeyVals.length) {
        layer.msg("请选择要操作的行");
        return false;
    }
    var key = lastKeyInfo.key;
    var score = lastKeyVals[row].score;
    var member = lastKeyVals[row].member;
    var callback = function (index, val1, val2) {
        var newScore = val1;
        var newMember = val2;
        if (inputValIsEmpty('分数', newScore)) {
            return;
        }
        if (inputValIsEmpty('成员', newMember)) {
            return;
        }

        if (!scoreIsValid(newScore)) {
            return;
        }
        layer.close(index);

        callJava("DataSinglesController", "updateVal",
            2, key, member, newScore, newMember,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    // layer.msg(result.msgs);
                }
            });
    };

    openTwoInputLayer('修改第'+(row+1)+'行有序集合成员', '分数', '成员', score, member, false, callback);
}

/**----------------------list start----------------------*/
//新增list的item
function insertList() {
    if (!nodeSelected || nodeSelected.isParent) {
        layer.msg("请选择要操作的KEY");
        return false;
    }

    var key = nodeSelected.name;
    var callback = function (index, text) {
        if (inputValIsEmpty('元素值', text)) {
            return;
        }
        layer.close(index);
        callJava("DataSinglesController", "sendCommandLine",
            "RPUSH", key, text,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    layer.msg(result.msgs);
                }
            });
    };
    openOneInputLayer('列表添加新元素', '元素值', '', true, callback);
}

//删除list的item
function deleteVals(type) {
    var table = layui.table;
    var checkStatus = table.checkStatus('key-vals-table');
    if (!checkStatus.data || !checkStatus.data.length) {
        layer.msg('请先勾选第一列选择要操作的行');
        return false;
    }

    var fieldName;
    if (type == 2)
        fieldName = 'member';
    else
        fieldName = 'field';
    var strRows = '';
    var fields = new Array();
    for( var j = 0,len=checkStatus.data.length; j < len; j++) {
        if (j != 0)
            strRows += ', ';
        strRows += checkStatus.data[j].row;
        fields.push({field:checkStatus.data[j][fieldName]});
    }

    var key = nodeSelected.name;
    var callback = function () {
        callJava("DataSinglesController", "delVal", type, key, JSON.stringify(fields),
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    layer.msg(result.msgs);
                }
            });
    };
    openDelConfirmLayer(key + '的行', strRows, callback);
}

//修改list的item
function updateList(row) {
    if (!lastKeyInfo) {
        layer.msg("请选择要操作的KEY");
        return false;
    }
    if (row < 0 || !lastKeyVals || row >= lastKeyVals.length) {
        layer.msg("请选择要操作的行");
        return false;
    }
    var key = lastKeyInfo.key;
    var val = lastKeyVals[row].field;

    var callback = function (index, text) {
        if (inputValIsEmpty('元素值', text)) {
            return;
        }
        layer.close(index);
        callJava("DataSinglesController", "updateVal",
            3, key, val, ''+row, text,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    layer.msg(result.msgs);
                }
            });
    };
    openOneInputLayer('修改第'+(row+1)+'行列表元素', '元素值', val, true, callback);
}

/**----------------------hash end----------------------*/
//增加hash的item
function insertHash() {
    var key = nodeSelected.name;
    var callback = function (index, val1, val2) {
        var field = val1;
        var value = val2;
        // console.log("insertHash field:" + field + " value:" + value);
        if (inputValIsEmpty('字段', field)) {
            return;
        }
        if (inputValIsEmpty('值', value)) {
            return;
        }
        layer.close(index);
        // console.log("insertHash callJava field:" + field + " value:" + value);
        callJava("DataSinglesController", "sendCommandLine",
            "HSET", key, field, value,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    // layer.msg(result.msgs);
                }
            });
    };

    openTwoInputLayer('哈希表添加新字段', '字段', '值', '', '', false, callback);
}

//修改hash的item
function updateHash(row) {
    if (!lastKeyInfo) {
        layer.msg("请选择要操作的KEY");
        return false;
    }
    if (row < 0 || !lastKeyVals || row >= lastKeyVals.length) {
        layer.msg("请选择要操作的行");
        return false;
    }
    var key = lastKeyInfo.key;
    var field = lastKeyVals[row].field;
    var value = lastKeyVals[row].value;
    var callback = function (index, val1, val2) {
        var newField = val1;
        var newValue = val2;
        if (inputValIsEmpty('字段', newField)) {
            return;
        }
        if (inputValIsEmpty('值', newValue)) {
            return;
        }
        layer.close(index);

        callJava("DataSinglesController", "updateVal",
            4, key, field, newField, newValue,
            function (result) {
                if (result.code === 200) {
                    getKeysInfo();
                    // layer.msg(result.msgs);
                }
            });
    };
    openTwoInputLayer('修改第'+(row+1)+'行哈希表字段', '字段', '值', field, value, false, callback);
}

function getTreeParent(key) {
    if (!key || key == ' ' || key == ''
        || key == '*' || key.indexOf(':') <= 0)
    { // return the Root node
        var treeParent = $.fn.zTree.getZTreeObj("keyTree").getNodeByParam("id", "root");
        if (treeParent && treeParent.isParent)
            return treeParent;
        return null;
    }

    var param = {
        parentFound : '',
        parentLen : 0,
        key : key,
        keyLen: key.length,
        patternLen : 0
    }

    var node = $.fn.zTree.getZTreeObj("keyTree").getNodesByFilter(
        function (node, param) {
            if (node.isParent) {
                param.patternLen = node.pattern.length;
                if (param.keyLen > param.patternLen // Key的长度大于目录名长度
                    && param.patternLen > param.parentLen // 寻找最长的目录名
                    && param.key.charAt(param.patternLen) == ':' // 避免zdytest:222:zset添加到zdytest:2
                    && param.key.startsWith(node.pattern)) // Key位于此目录之下
                {
                    param.parentLen = param.patternLen;
                    param.parentFound = node;
                }
            }
            return false;
        },
        true, null, param);

    if (!node) {
        return param.parentFound;
    }
    return node;
}
