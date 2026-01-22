# Crystal Palace 测试会话记录

日期: 2026-01-22

## 测试摘要

成功完成所有 Tradecraft Garden (tcg) 测试套件测试。

### 测试结果

| 测试用例 | x86 | x64 | 输出大小 |
|---------|-----|-----|---------|
| simple_rdll | ✅ | ✅ | 80-87KB |
| simple_obj | ✅ | ✅ | ~2KB |
| simple_objmix | ✅ | ✅ | 80-91KB |
| simple_pic | ✅ | ✅ | 79-86KB |
| simple_rdll_free | ✅ | ✅ | 81-88KB |
| simple_rdll_mask | ✅ | ✅ | 91-98KB |
| simple_rdll_patch | ✅ | ✅ | 81-88KB |
| simple_rdll_hook | ✅ | ✅ | 82-89KB |
| simple_guardrail | ✅ | ✅ | 84-91KB |
| pagestream | ✅ | ✅ | 84-91KB |
| rdllinjection | ✅ | ✅ | 81-88KB |

**总计: 11 个测试用例, 22 个测试 (x86 + x64), 全部通过**

## 测试环境

- **操作系统**: Windows (MSYS2/MinGW)
- **Java 版本**: Java 24
- **编译器**: MinGW (i686-w64-mingw32-gcc, x86_64-w64-mingw32-gcc)
- **Crystal Palace JAR**: build/crystalpalace.jar

## 测试依赖

1. **libtcg 库** - 所有测试依赖的共享库
   - libtcg.x86.zip
   - libtcg.x64.zip
   - 包含: loaddll.o, resolve_eat.o, picorun.o, debug.o, util.o

2. **测试 DLL** - 用于链接测试的 DLL
   - testdll.x86.dll
   - testdll.x64.dll

## 编译说明

### 编译 libtcg
```bash
export PATH="/c/msys64/mingw32/bin:/c/msys64/mingw64/bin:$PATH"
cd tcg/libtcg && make
```

### 编译其他测试组件
```bash
cd tcg/<test_case> && make
```

### 运行测试
```bash
export PATH="/c/Users/root/.version-fox/cache/java/current/bin:$PATH"
java -Dcrystalpalace.verbose=false -cp build/crystalpalace.jar \
    crystalpalace.spec.LinkerCLI run <spec_file> <dll_file> <output_file> [args...]
```

## 发现的问题及修复

1. **Java 版本兼容性**: 需要使用 Java 24 运行时 (不是 1.8)
2. **ZIP 打包**: 使用 `jar cMf` (带 M 标志) 避免创建 META-INF 目录
3. **simple_rdll_mask**: 需要额外编译 free.c

## 技术决策

- 使用 `jar cMf` 替代 `zip` 命令创建库归档文件
- 测试 DLL 使用简单的导出函数进行验证

## 关键文件位置

- 测试用例: `tcg/`
- 库文件: `tcg/libtcg/`
- 测试 DLL: `tcg/testdll.*.dll`
- 输出文件: `tcg/<test_case>/bin/*.bin`
