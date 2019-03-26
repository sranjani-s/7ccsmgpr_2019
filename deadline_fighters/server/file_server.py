#!/usr/bin/env python

from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
import SocketServer

import boto3 #AWS S3 API in Python
import simplejson #Interpret incoming JSON
import json 
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

session = boto3.Session(profile_name='default')
s3 = session.client('s3')

###### AWS Operations ######

def upload_file(fileName):
		print("Call for upload")
		url = s3.generate_presigned_url('put_object', Params = {'Bucket': bucketName, 'Key': fileName}, ExpiresIn = 100, HttpMethod = 'PUT')
		return json.dumps({'url':url})

def download_file(fileName):
		print("Call for download")
		url = s3.generate_presigned_url('get_object', Params = {'Bucket': bucketName, 'Key': fileName}, ExpiresIn = 100)
		return json.dumps({'url':url})

def delete_file(fileName):
		print("Call for delete")
		url = s3.generate_presigned_url('delete_object', Params = {'Bucket': bucketName, 'Key': fileName}, ExpiresIn = 100)
		return json.dumps({'url':url})


def download_All():
		print("Call for download_All")
		objectListDict = s3.list_objects(Bucket = bucketName)
		finalJson = []
		for objects in objectListDict['Contents']:
			itemJson = dict()
			itemJson['fileName'] = objects['Key']
			urlJson = download_file(objects['Key'])
			itemJson['url'] = json.loads(urlJson)['url']
			finalJson.append(itemJson)
		return json.dumps({'downloadAll':finalJson})

def upload_All():
		for f in listdir(syncDir):
			if isfile(join(syncDir,f)):
				upload_file(f)
		print("All files uploaded from "+homeDir)
		return

#Sync: Pull before Push
def sync_All(fileName):
		print("Call for sync_All")
		return download_All()
		


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
		self._set_headers()

		print "in post method"
		self.data_string = self.rfile.read(int(self.headers['Content-Length']))

		data = simplejson.loads(self.data_string)

		func = switcher.get(data["operation"], "Invalid operation")

		fileDetails = data["fileDetails"]

		if type(fileDetails) is dict:
			fileName = fileDetails["fileName"]
		else:
			fileName = fileDetails

		jsonResponse = func(fileName)
		if jsonResponse is not None: 
			self.wfile.write(jsonResponse.encode('utf-8'))

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
