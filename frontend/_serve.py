"""Serveur local HireLink frontend — port 5500 (à lancer depuis IntelliJ ou terminal)."""
import http.server
import socketserver
import os

PORT = 5500
os.chdir(os.path.dirname(os.path.abspath(__file__)))

Handler = http.server.SimpleHTTPRequestHandler
with socketserver.TCPServer(("", PORT), Handler) as httpd:
    print(f"HireLink frontend: http://localhost:{PORT}")
    print(f"Login: http://localhost:{PORT}/auth.html?mode=login")
    httpd.serve_forever()
