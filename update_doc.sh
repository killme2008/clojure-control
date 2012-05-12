lein deps
lein autodoc
cd autodoc
git add -A
git commit -m"Documentation update"
git push origin gh-pages