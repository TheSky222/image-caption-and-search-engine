#!/usr/bin/env python
# -*- coding: utf-8 -*-
from flask import Flask, jsonify, request
from flask_cors import CORS
import os
import replicate
from translate import Translator
import requests
import hashlib   # 用来计算MD5码

# configuration
DEBUG = True
UPLOAD_FOLDER = r'./uploads'

# instantiate the app
app = Flask(__name__)
app.config.from_object(__name__)
app.secret_key = 'secret!'
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

# enable CORS 跨域设置
CORS(app, resources={r'/*': {'origins': '*'}})
# CORS.init_app(app)

def fanyi(shuru):
    header = {
        'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.96 Safari/537.36',
        'Content-Type': 'application/x-www-form-urlencoded'
    }
    appid, salt, key = "20230420001649424", "20010222", "9lXNkTFDLNtpVpkvagSt"
    q = shuru
    sign = appid+q+salt+key
    md5 = hashlib.md5()
    md5.update(sign.encode('utf-8'))  # 生成签名计算MD5码
    data = {
        "q": q,
        "from": "auto",
        "to": "zh",
        "appid": appid,
        "salt": salt,
        "sign": md5.hexdigest()
    }
    response = requests.post('https://fanyi-api.baidu.com/api/trans/vip/translate',headers=header, data=data )  # 发送post请求
    text = response.json()  # 返回的为json格式用json接收数据
#     print(text)
    shuchu = text['trans_result'][0]['dst']
    return shuchu

# 添加header解决跨域
@app.after_request
def after_request(response):
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Credentials'] = 'true'
    response.headers['Access-Control-Allow-Methods'] = 'POST'
    response.headers['Access-Control-Allow-Headers'] = 'Content-Type, X-Requested-With'
    return response

# sanity check route


@app.route('/open', methods=['GET'])
def open_door():
    return jsonify('芝麻开门！123')


@app.route('/image', methods=['POST'])
def get_image():
    file = request.args.get()
    print(file)
    return


@app.route('/clip', methods=['GET', 'POST'])
def image_caption():
    # print(request.files)
    file = request.files['file']
    # print(file)
    # print(file.filename)
    src_path = os.path.join(app.config['UPLOAD_FOLDER'], file.filename)
    # print(src_path)
    file.save(src_path)
    os.environ["REPLICATE_API_TOKEN"] = "9628f53cd9211bbe8b376778218135cb39f68359"
    model = replicate.models.get("rmokady/clip_prefix_caption")
    version = model.versions.get("9a34a6339872a03f45236f114321fb51fc7aa8269d38ae0ce5334969981e4cd8")
    inputs = {
        # Input image
        # 'image': open("./cat.JPG", "rb"),
        'image': open(src_path, "rb"),
        # Choose a model
        'model': "coco",

        # Whether to apply beam search to generate the output text
        'use_beam_search': False,
    }
    # Set the REPLICATE_API_TOKEN environment variable
    output = version.predict(**inputs)

    output_cn = fanyi(output)
    # print(output)

    return jsonify({'output': output_cn})



if __name__ == '__main__':
    app.config['JSON_AS_ASCII'] = False  # 防止json被转化为ascii码

    # 域名设为 localhost 让 localhost、127.0.0.1 都能访问 Flask 框架默认使用 127.0.0.1 回环地址作为启动域名，因此无法使用 localhost 访问
    app.run(host="localhost")
