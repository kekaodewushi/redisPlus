
function toDataPage(fromConn) {
    if (fromConn) {
        // console.log("rowData:" + rowData);
        if (!rowData) {
            var table = layui.table;
            var checkStatus = table.checkStatus('dataList');
            // console.log("checkStatus:" + checkStatus);
            if (!checkStatus.data || !checkStatus.data.length) {
                layer.alert('请勾选或双击某行, 选择连接方向', {
                    skin: 'layui-layer-lan',
                    title: "操作提示"
                });
                return false;
            }
            rowData = checkStatus.data[0];
        }
        callJava("ConnectController", "checkConnect", rowData.name, true, function (result) {
            if (result.code == 200)
                jsCallJava.changeWebView(true);
        });
    } else {
        jsCallJava.changeWebView(true);
    }
}

function toConnPage() {
    jsCallJava.changeWebView(false);
}

function isJSON(str) {
    if (typeof str == 'string') {
        try {
            var obj=JSON.parse(str);
            if(typeof obj == 'object' && obj ){
                return true;
            }else{
                return false;
            }
        } catch(e) {
            // console.log('error: '+str+'!!!'+e);
            return false;
        }
    }
    // console.log('It is not a string!')
    return false;
}
//
// function quoteattr(s) {
//     return s.replace(/&/g, '&amp;') /* This MUST be the 1st replacement. */
//         .replace(/'/g, '&apos;') /* The 4 other predefined entities, required. */
//         .replace(/"/g, '&quot;')
//         .replace(/</g, '&lt;')
//         .replace(/>/g, '&gt;')
//         .replace(/\r/g, '');
// }

function formatDate(time) {
    var format = "YY-MM-DD hh:mm:ss";
    var date = new Date(time);
    var year = date.getFullYear(), month = date.getMonth() + 1, // 月份是从0开始的
        day = date.getDate(), hour = date.getHours(), min = date.getMinutes(), sec = date
            .getSeconds();
    var preArr = Array.apply(null, Array(10)).map(function (elem, index) {
        return '0' + index;
    });// //开个长度为10的数组 格式为 00 01 02 03
    var newTime = format.replace(/YY/g, year).replace(/MM/g,
        preArr[month] || month).replace(/DD/g, preArr[day] || day).replace(
        /hh/g, preArr[hour] || hour).replace(/mm/g, preArr[min] || min)
        .replace(/ss/g, preArr[sec] || sec);
    return newTime;
}

function formatTimestamp(data) {
    var ts = arguments[0] || 0;
    var t, y, m, d, h, i, s;
    t = ts ? new Date(ts * 1000) : new Date();
    y = t.getFullYear();
    m = t.getMonth() + 1;
    d = t.getDate();
    h = t.getHours();
    i = t.getMinutes();
    s = t.getSeconds();
    return y + '-' + (m < 10 ? '0' + m : m) + '-' + (d < 10 ?
        '0' + d : d) + ' ' + (h < 10 ? '0' + h : h) + ':' + (i < 10 ? '0' + i : i) + ':' + (s < 10 ? '0' + s : s);
}

/* 之所以改为异步, 是因为UI线程调用堆栈进入Java代码执行后,
    单线程的js被堵死, layer.load()之类的效果显示不了, 界面卡死了
 */
var errJsonObj = JSON.parse('{"code": "500"}');
function callJava() {

    /*  Step1: 参数检查 */
    var callback = null;
    if (arguments.length >= 1)
        callback = arguments[arguments.length-1];
    if (typeof callback != 'function')
        callback = null;

    var argsToJava = arguments.length;
    if (callback)
        argsToJava--;
    if (argsToJava < 2) {
        layer.msg("callJava()参数错误, 要求传入Controller名以及函数名", {
            skin: 'layui-layer-lan',
            title: "错误提示",
            closeBtn: 0
        });
        return errJsonObj;
    }

    if (!jsCallJava) {
        layer.msg("jjsCallJava对象为空, 需先在Java代码中注册", {
            skin: 'layui-layer-lan',
            title: "错误提示",
            closeBtn: 0
        });
        return errJsonObj;
    }

    /*  Step2: 通知Java程序执行相应Controller中的方法 */
    var jsonStr;
    switch(argsToJava) {
        case 2:
            jsonStr = jsCallJava.call2(arguments[0], arguments[1]);
            break;
        case 3:
            jsonStr = jsCallJava.call3(arguments[0], arguments[1], arguments[2]);
            break;
        case 4:
            jsonStr = jsCallJava.call4(arguments[0], arguments[1], arguments[2], arguments[3]);
            break;
        case 5:
            jsonStr = jsCallJava.call5(arguments[0], arguments[1], arguments[2],
                arguments[3], arguments[4]);
            break;
        case 6:
            jsonStr = jsCallJava.call6(arguments[0], arguments[1], arguments[2],
                arguments[3], arguments[4], arguments[5]);
            break;
        case 7:
            jsonStr = jsCallJava.call7(arguments[0], arguments[1], arguments[2],
                arguments[3], arguments[4], arguments[5], arguments[6]);
            break;
        case 8:
            jsonStr = jsCallJava.call8(arguments[0], arguments[1], arguments[2],
                arguments[3], arguments[4], arguments[5], arguments[6], arguments[7]);
            break;
        case 9:
            jsonStr = jsCallJava.call9(arguments[0], arguments[1], arguments[2],
                arguments[3], arguments[4], arguments[5], arguments[6], arguments[7],
                arguments[8]);
            break;
        case 10:
            jsonStr = jsCallJava.call10(arguments[0], arguments[1], arguments[2],
                arguments[3], arguments[4], arguments[5], arguments[6], arguments[7],
                arguments[8], arguments[9]);
            break;
        default:
            layer.alert("callJava()参数错误, 参数太多", {
                skin: 'layui-layer-lan',
                title: "错误提示",
                closeBtn: 0
            });
            return errJsonObj;
    }
   //console.log("jsCallJava.call: " + arguments[0] + "." + arguments[1] + "(), result:" + jsonStr);

    var result = JSON.parse(jsonStr);
    if (result.code != 200) {
        return jsonStr;
    }
    var requestId = result.msgs;

    /*  Step3: 根据返回的Id获取Java程序执行结果 */
   asyncHandleResult(requestId, callback);
}

function resolveResult(requestId) {
    return new Promise(resolve => {
        var getResultTimes = 0;
        var d = setInterval(function () {
            //console.log(new Date().toLocaleTimeString() + ' start jsCallJava.getResult requestId:' + requestId);
            var result = jsCallJava.getResult(requestId);
            if (result) {
                clearInterval(d);
                //console.log(new Date().toLocaleTimeString() + ' end jsCallJava.getResult requestId:' + requestId +
                //     " result:" + result);
                resolve(result);
            } else {
                getResultTimes++;
                // 不要一开始就转, 否则一闪一闪的很烦, 回应慢的时候转
                if (getResultTimes == 6)
                    layer.load(2);
                //console.log(new Date().toLocaleTimeString() + ' end jsCallJava.getResult requestId:' + requestId +
                //     " no result");
            }
        }, 100);
});
}

async function asyncHandleResult(requestId, func) {
    //console.log(new Date().toLocaleTimeString() + " start asyncHandleResult requestId: " + requestId);
    var result = await resolveResult(requestId);
    //console.log(new Date().toLocaleTimeString() + "end asyncHandleResult requestId: " + requestId + " result:" + result);
    result = JSON.parse(result);
    if (result.code != 200) {
        layer.closeAll('loading');
        layer.alert(result.msgs, {
            skin: 'layui-layer-lan',
            title: "错误提示",
            closeBtn: 0
        });
    }
    if (func) func(result);
    if (result.code == 200)
        layer.closeAll('loading');
}
