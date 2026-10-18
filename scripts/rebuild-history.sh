#!/usr/bin/env bash
# 从 git 历史重建各版本的构建产物，归档到 release/<项目名>/
# 用法：bash scripts/rebuild-history.sh
set -u

REPO="D:/code/Plugin/mcPligin"
RB="/tmp/rebuild"
GRADLE="$REPO/gradlew"

cd "$REPO" || exit 1
mkdir -p "$RB" release/MainMenu release/MainMenuRepair

# 提交:版本（MainMenu 插件）
PLUGIN_VERSIONS="eeef7b7:Beta_0_1_0
6d6cc5c:Beta_0_2_0
b913340:Beta_0_2_1
67e70a6:Beta_0_3_0
c80a3f6:Beta_0_3_1
e840d65:Beta_0_4_0
abc1ded:Beta_0_5_0"

# 提交:版本（MainMenuRepair 模组）
MOD_VERSIONS="5ee50b8:Beta_0_1_0
e54c905:Beta_0_1_1
3567f41:Beta_0_1_2
04e0a64:Beta_0_1_3
cbc550b:Beta_0_1_4
a0c8374:Beta_0_1_5
00293c0:Beta_0_1_6
6167758:Beta_0_1_7
2167a30:Beta_0_1_8
103105d:Beta_0_2_0
abc1ded:Beta_0_3_0"

ok=0
fail=0

rebuild_plugin() {
    local commit="$1" ver="$2"
    local dir="$RB/MainMenu-$ver"
    local jar="MainMenu-mc26_3-plugin-$ver.jar"
    rm -rf "$dir"
    git worktree add --detach "$dir" "$commit" >/dev/null 2>&1 || { echo "FAIL(worktree) $commit"; fail=$((fail+1)); return; }
    # 套用当前构建脚本：命名规范 + 本机可编译（旧提交里的工具链配置在本机会 fork 失败）
    cp build.gradle settings.gradle gradle.properties "$dir/"
    cp -r gradle "$dir/" 2>/dev/null
    cp gradlew gradlew.bat "$dir/" 2>/dev/null
    sed -i "s/^version = .*/version = '$ver'/" "$dir/Minecraft-26_3-plugin/MainMenu/build.gradle"
    ( cd "$dir" && "$GRADLE" :MainMenu:jar -q --console=plain >/tmp/rebuild-plugin.log 2>&1 )
    if [ -f "$dir/Minecraft-26_3-plugin/MainMenu/build/libs/$jar" ]; then
        cp "$dir/Minecraft-26_3-plugin/MainMenu/build/libs/$jar" release/MainMenu/
        echo "OK   MainMenu $ver  ← $commit"
        ok=$((ok+1))
    else
        echo "FAIL(build)   MainMenu $ver  ← $commit（见 /tmp/rebuild-plugin.log）"
        fail=$((fail+1))
    fi
    git worktree remove --force "$dir" >/dev/null 2>&1
}

rebuild_mod() {
    local commit="$1" ver="$2"
    local dir="$RB/mod-$ver"
    local proj="$dir/fabric-26_3-mod/MainMenuRepair"
    local jar="MainMenuRepair-mc26_3-fabric-$ver.jar"
    rm -rf "$dir"
    git worktree add --detach "$dir" "$commit" >/dev/null 2>&1 || { echo "FAIL(worktree) $commit"; fail=$((fail+1)); return; }
    cp fabric-26_3-mod/MainMenuRepair/build.gradle fabric-26_3-mod/MainMenuRepair/settings.gradle "$proj/" 2>/dev/null
    cp fabric-26_3-mod/MainMenuRepair/gradle.properties "$proj/" 2>/dev/null
    sed -i "s/^version=.*/version=$ver/" "$proj/gradle.properties"
    # 复用已下载的客户端 jar，避免每个 worktree 重新下 41MB
    mkdir -p "$proj/build/minecraft"
    cp fabric-26_3-mod/MainMenuRepair/build/minecraft/client-26.3.jar "$proj/build/minecraft/" 2>/dev/null
    ( cd "$proj" && "$GRADLE" build -q --console=plain >/tmp/rebuild-mod.log 2>&1 )
    if [ -f "$proj/build/libs/$jar" ]; then
        cp "$proj/build/libs/$jar" release/MainMenuRepair/
        echo "OK   MainMenuRepair $ver  ← $commit"
        ok=$((ok+1))
    else
        echo "FAIL(build)   MainMenuRepair $ver  ← $commit（见 /tmp/rebuild-mod.log）"
        fail=$((fail+1))
    fi
    git worktree remove --force "$dir" >/dev/null 2>&1
}

echo "===== MainMenu 插件历史版本 ====="
while IFS=: read -r commit ver; do
    [ -z "$commit" ] && continue
    rebuild_plugin "$commit" "$ver"
done <<< "$PLUGIN_VERSIONS"

echo "===== MainMenuRepair 模组历史版本 ====="
while IFS=: read -r commit ver; do
    [ -z "$commit" ] && continue
    rebuild_mod "$commit" "$ver"
done <<< "$MOD_VERSIONS"

echo "===== 完成：成功 $ok，失败 $fail ====="
