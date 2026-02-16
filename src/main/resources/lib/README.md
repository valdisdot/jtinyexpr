This is original C source code from [tinyexpr](https://github.com/codeplea/tinyexpr)

To compile into native libraries (assuming you have Linux environment):

- Linux lib (.so)

```bash
cd resources/lib
gcc -c -fPIC tinyexpr.c -o tinyexpr.o && gcc -shared -o tinyexpr-amd64.so tinyexpr.o -lm && rm tinyexpr.o
```

- Windows lib (.dll)

```bash
sudo apt-get update
sudo apt install mingw-w64

cd resources/lib
x86_64-w64-mingw32-gcc -shared -o tinyexpr-amd64.dll tinyexpr.c -lm -Wl,--out-implib,tinyexpr.a && rm tinyexpr.a
```

Or run `compile.sh`