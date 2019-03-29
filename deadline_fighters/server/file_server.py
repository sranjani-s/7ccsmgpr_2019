#!/usr/bin/env python

from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
import SocketServer

import boto3 #AWS S3 API in Python
import simplejson #Interpret incoming JSON
import json 
import os #Get current directory
import sys #Check platform
import botocore
import sqlite3
import datetime # For logging
from sqlite3 import Error

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
logDir = homeDir+"/serverLogs/";

session = boto3.Session(profile_name='default')
s3 = session.client('s3')


###### Utility functions #######

def log(string):
	if not os.path.exists(logDir):
		os.makedirs(logDir)
	with open(logDir+datetime.datetime.today().strftime('%Y-%m-%d')+".txt", 'a+') as f:
		print >> f, datetime.datetime.now(),string

def create_connection(db_file):
	try:
		conn = sqlite3.connect(db_file)
		return conn
	except Error as e:
		print(e)
 
	return None

def create_table(conn, create_table_sql):
	try:
		c = conn.cursor()
		c.execute(create_table_sql)
	except Error as e:
		print(e)


###### DB functions #######
def insert_entry(conn,entry):
	log("Inside insert_entry")
	sql = '''	INSERT OR REPLACE INTO etag(fileName,etag,lastModified)
				VALUES(?,?,?)'''
	cur = conn.cursor()
	cur.execute(sql, entry)
	conn.commit()
	return cur.lastrowid


###### AWS Operations ######

def updateDB():
	log("Updating DB...")
	objectListDict = s3.list_objects(Bucket = bucketName)
	if 'Contents' in objectListDict:
		log("Contents present to update DB")
		for objects in objectListDict['Contents']:
			entry = (objects['Key'],objects['ETag'],objects['LastModified']);
			insert_entry(conn,entry)

			


def upload_file(fileName):
		log("Call for upload for file "+ fileName)
		url = (s3.generate_presigned_url('put_object', Params = {'Bucket': bucketName, 'Key': fileName}, ExpiresIn = 100))
		log(" Upload URL fetched "+ url)
		return json.dumps({'url':url})

def download_file(fileName):
		log("Call for download for file "+ fileName)
		url = s3.generate_presigned_url('get_object', Params = {'Bucket': bucketName, 'Key': fileName}, ExpiresIn = 100)
		log(" Download URL fetched "+ url)
		return json.dumps({'url':url})

def delete_file(fileName):
		log("Call for delete for file "+ fileName)
		url = s3.generate_presigned_url('delete_object', Params = {'Bucket': bucketName, 'Key': fileName}, ExpiresIn = 100)
		log(" Delete URL fetched "+ url)
		return json.dumps({'url':url})


def download_All():
		log("Call for download_All")
		objectListDict = s3.list_objects(Bucket = bucketName)
		finalDownloadAllJson = []
		if 'Contents' in objectListDict:
			for objects in objectListDict['Contents']:
				itemJson = dict()
				itemJson['fileName'] = objects['Key']
				urlJson = download_file(objects['Key'])
				itemJson['url'] = json.loads(urlJson)['url']
				finalDownloadAllJson.append(itemJson)
		return json.dumps({'downloadAll':finalDownloadAllJson})

def upload_All():
		log("Call for upload_All")
		finalUploadAllJson = []
		for f in listdir(syncDir):
			if isfile(join(syncDir,f)):
				itemJson = dict()
				itemJson['fileName'] = f
				urlJson = upload_file(f)
				itemJson['url'] = json.loads(urlJson)['url']
				finalUploadAllJson.append(itemJson)
		return json.dumps({'uploadAll':finalUploadAllJson})

#Sync: Pull before Push
def sync_All(fileName):
		log("Call for sync_All")
		finalSyncAllJson =[]
		downloadAllJson = download_All()
		finalSyncAllJson.append(downloadAllJson)
		uploadAllJson = upload_All()
		finalSyncAllJson.append(uploadAllJson)
		return json.dumps({'syncAll':finalSyncAllJson})

def list_All(fileName):
		log("Call for list_All")
		objectListDict = s3.list_objects(Bucket = bucketName)
		log(objectListDict)
		finalListAllJson = []
		if 'Contents' in objectListDict:
			for objects in objectListDict['Contents']:
				itemJson = dict()
				itemJson['fileName'] = objects['Key']
				urlJson = download_file(objects['Key'])
				finalListAllJson.append(itemJson)
			return json.dumps({'listAll':finalListAllJson})
		return json.dumps({})

def error():
		log("Call for error")
		return json.dumps({'Message':'Function not found'})

switcher = {
	"downloadAll": download_All,
	"uploadFile": upload_file,
	"downloadFile": download_file,
	"deleteFile": delete_file,
	"uploadAll": upload_All,
	"syncAll": sync_All,
	"listAll": list_All,
	"Invalid operation": error
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

		self.data_string = self.rfile.read(int(self.headers['Content-Length']))

		data = simplejson.loads(self.data_string)

		log("Received POST call of type "+ data["operation"])

		func = switcher.get(data["operation"], "Invalid operation")

		fileDetails = data["fileDetails"]

		if type(fileDetails) is dict:
			fileName = fileDetails["fileName"]
		else:
			fileName = fileDetails

		log("File name in "+ data["operation"] + " is "+fileName)

		jsonResponse = func(fileName)
		if jsonResponse is not None: 
			self.wfile.write(jsonResponse.encode('utf-8'))

def run(server_class=HTTPServer, handler_class=S, port=80):
	server_address = ('', port)
	httpd = server_class(server_address, handler_class)
	log("<------Starting new session------------>")
	print ('Starting deadlinefighters server...')
	httpd.serve_forever()
	return

if __name__ == "__main__":
	from sys import argv

	if len(argv) == 2:
		run(port=int(argv[1]))
	else:
		run()
