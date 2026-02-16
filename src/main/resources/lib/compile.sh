gcc -c -fPIC tinyexpr.c -o tinyexpr.o
gcc -shared -o tinyexpr-amd64.so tinyexpr.o -lm
rm tinyexpr.o

x86_64-w64-mingw32-gcc -shared -o tinyexpr-amd64.dll tinyexpr.c -lm -Wl,--out-implib,tinyexpr.a
rm tinyexpr.a

mkdir ../../../test/resources/lib
ln tinyexpr-amd64.so ../../../test/resources/lib/tinyexpr-amd64.so
ln tinyexpr-amd64.dll ../../../test/resources/lib/tinyexpr-amd64.dll

