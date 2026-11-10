#!/bin/sh

if [ -n "$TMDB_API_KEY" ]; then
  echo "Injecting TMDB_API_KEY into index.html"
  sed -i "s|const TMDB_API_KEY = \".*\";|const TMDB_API_KEY = \"$TMDB_API_KEY\";|" /app/public/index.html
else
  echo "Warning: TMDB_API_KEY not set."
fi

echo "DongguaTV backend starting (modular, src/app.js)..."
exec node src/app.js
