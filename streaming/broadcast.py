from flask import Flask, render_template
from opentok import OpenTok
import webbrowser
import os

try:
    api_key = "[API_KEY]"
    api_secret =  "[API_SECRET]"
except Exception:
    raise Exception('You must define API_KEY and API_SECRET environment variables')

app = Flask(__name__)
opentok = OpenTok(api_key, api_secret)

@app.route("/")
def hello():
    key = api_key
    session_id = "[get a session id per hackster instruction]"
    token = opentok.generate_token(session_id)
    return render_template('index.html', api_key=key, session_id=session_id, token=token)

if __name__ == "__main__":
    app.debug = True
    app.run()


print('doh')
url = "http://127.0.0.1:5000/"
webbrowser.open(url)

