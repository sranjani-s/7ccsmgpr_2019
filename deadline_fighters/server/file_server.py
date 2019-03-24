#!/usr/bin/env python

from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
import SocketServer

import boto3 #AWS S3 API in Python
import simplejson #Interpret incoming JSON
import json #Interpret incoming JSON
import os #Get current directory
import sys #Check platform
import botocore
import mysql.connector
import sqlite3

from shutil import copyfile
from os.path import expanduser,isfile,join
from os import listdir

##### Initial Setup ######

if sys.platform == 'darwin':
    homeDir = expanduser("~")
else:
    homeDir = "D:/"

s3_resource = boto3.resource('s3');
bucketName = "deadlinefighters";
syncDir = homeDir+"/deadlinefighters/";



###### AWS Operations ######

def upload_file(fileName):
        copyfile(syncDir+fileName,os.getcwd()+"/"+fileName)
        s3_resource.Object(bucketName, fileName).upload_file(
    Filename=fileName)
        os.remove(os.getcwd()+"/"+fileName)
        print("Uploaded file "+fileName)
        return

def download_file(fileName):
        try:
            s3_resource.Bucket(bucketName).download_file(fileName,syncDir+fileName)
            print("Downloaded file "+fileName)
        except botocore.exceptions.ClientError as e:
            if e.response['Error']['Code'] == "404":
                print("The object does not exist.")
            else:
                raise
        return

def delete_file(fileName):
        print(bucketName)
        print(fileName)
        s3_resource.Object(bucketName, fileName).delete()
        print("Deleted file "+fileName)
        return


def download_All():
        bucket = s3_resource.Bucket(bucketName)
        for s3_object in bucket.objects.all():
            path, filename = os.path.split(s3_object.key)
            download_file(filename)
        print("All files downloaded from bucket "+bucketName)
        return

def upload_All():
        for f in listdir(syncDir):
            if isfile(join(syncDir,f)):
                upload_file(f)
        print("All files uploaded from "+homeDir)
        return

#Sync: Pull before Push
def sync_All(fileName):
        download_All()
        upload_All()
        print("SyncAll complete!")


switcher = {
    "downloadAll": download_All,
    "uploadFile": upload_file,
    "downloadFile": download_file,
    "deleteFile": delete_file,
    "uploadAll": upload_All,
    "syncAll": sync_All
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

        fileDetails = data["fileDetails"]

        if type(fileDetails) is dict:
            fileName = fileDetails["fileName"]
        else:
            fileName = fileDetails

        return  func(fileName)

def run(server_class=HTTPServer, handler_class=S, port=80):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print 'Starting deadlinefighters server...'
    httpd.serve_forever()
    return

if __name__ == "__main__":
    from sys import argv

    if len(argv) == 2:
        run(port=int(argv[1]))
    else:
        run()
