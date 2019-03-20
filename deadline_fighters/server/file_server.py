#!/usr/bin/env python

from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
import SocketServer

import boto3 #AWS S3 API in Python
import simplejson #Interpret incoming JSON
import json #Interpret incoming JSON
import os #Get current directory

from shutil import copyfile

from os.path import expanduser
homeDir = expanduser("~")

s3_resource = boto3.resource('s3');
bucketName = "deadlinefighters";
syncDir = homeDir+"/deadlinefighters/";

def download_All():
        print("Reached download_All!")
        #Insert S3 code here

def upload_file(fileDetails):
        print("Entered upload_file")
        fileName = fileDetails["fileName"]
        copyfile(syncDir+fileName,os.getcwd()+"/"+fileName)
        s3_resource.Object(bucketName, fileName).upload_file(
    Filename=fileName)
        os.remove(os.getcwd()+"/"+fileName)
        return

def download_file(fileDetails):
        print("Entered download_file")
        fileName = fileDetails["fileName"]
        try:
            s3_resource.Bucket(bucketName).download_file(fileName,syncDir+fileName)
        except botocore.exceptions.ClientError as e:
            if e.response['Error']['Code'] == "404":
                print("The object does not exist.")
            else:
                raise

def delete_file(fileDetails):
        print("Entered delete_file")
        fileName = fileDetails["fileName"]
        s3_resource.Object(bucketName, fileName).delete()


switcher = {
    "downloadAll": download_All,
    "uploadFile": upload_file,
    "downloadFile": download_file,
    "deleteFile": delete_file
}

class S(BaseHTTPRequestHandler):
    def _set_headers(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()

    def do_GET(self):
        self._set_headers()
        self.wfile.write("<html><body><h1>hi!</h1></body></html>")

    def do_HEAD(self):
        self._set_headers()

    def do_POST(self):
        # Doesn't do anything with posted data
        self._set_headers()
        self.wfile.write("<html><body><h1>POST!</h1></body></html>")

        print "in post method"
        self.data_string = self.rfile.read(int(self.headers['Content-Length']))

        self.send_response(200)
        self.end_headers()

        data = simplejson.loads(self.data_string)

        func = switcher.get(data["operation"], "Invalid operation")

        return  func(data["fileDetails"])

def run(server_class=HTTPServer, handler_class=S, port=80):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print 'Starting httpd...'
    httpd.serve_forever()

if __name__ == "__main__":
    from sys import argv

    if len(argv) == 2:
        run(port=int(argv[1]))
    else:
        run()
