#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
WuLing Empire — GitHub 发布工具（走 REST API，不依赖 git push）

为什么不用 git push？
    WorkBuddy 沙箱注入了本地 HTTP 代理（HTTP(S)_PROXY=127.0.0.1:xxxxx），
    git 的 https 传输会被代理拒绝（CONNECT tunnel failed, response 502），
    绕开代理直连则被沙箱屏蔽（Could not connect to github.com:443）。
    而 Python 走 HTTP_PROXY 访问 api.github.com 是放行的（含资产上传），
    所以本脚本全程使用 GitHub REST API。

用法：
    python tools/publish_github.py status              # 只看本地与远程的差异
    python tools/publish_github.py sync                # 把源码变化提交并推送到 main
    python tools/publish_github.py sync -m "说明"      # 自定义提交信息
    python tools/publish_github.py release 0.3.0       # 正式版：tag v0.3.0 + 上传 wuling-empire-v0.3.0-beta.jar
    python tools/publish_github.py release 0.3.1-test  # 测试版：tag v0.3.1-test + 上传对应 jar
    python tools/publish_github.py sync --release      # 同步源码后再发布 jar

    产物路径按 `wuling-empire-<tag>-beta.jar` 拼（找不到就退到 `wuling-empire-<tag>*.jar`）；
    要用别的文件名再加 `--jar <路径>`，追加说明用 `-n "..."`。

设计要点：
    * 差异比对不用 git status / HEAD，而是「远程 tree 的 blob sha」对
      「本地 git hash-object --path」—— 后者会应用 core.autocrlf 与 .gitattributes，
      所以算出来的 sha 与 GitHub 上完全可比，不会出现 CRLF 假差异。
    * 上传内容直接从 `git cat-file blob` 取，与 sha 严格对应。
    * 二进制安全：blob 以 base64 提交，jar/png 不会被破坏。
    * 忽略规则交给 git（ls-files --others --exclude-standard），
      所以 .gitignore 里的 build/、_env/、.workbuddy/ 天然不会被上传。
    * 所有请求自动重试瞬时错误（代理 502 / HTTP 5xx / 429）；资产上传只在
      `uploads.github.com` 上重试（最多 8 次）—— 换 api.github.com 会 404。
"""

import base64
import glob
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OWNER = 'caianran1'
# 仓库 2026-10-02 由 Wuling-Empire 改名为 Wuling-Empire-for-Minecraft。
# 旧名仍然可用（GitHub 会 301 重定向），但 API 走旧名会多一跳，且未来可能失效，所以直接写新名。
REPO = 'Wuling-Empire-for-Minecraft'
BRANCH = 'main'
API = 'https://api.github.com'
UA = 'wuling-publish'


# ---------------------------------------------------------------- 基础工具

def token():
    t = os.environ.get('GITHUB_TOKEN')
    if t:
        return t.strip()
    p = os.path.expanduser('~/.git-credentials')
    try:
        for line in open(p, encoding='utf-8'):
            m = re.match(r'https?://([^:@/]+)(?::([^@]*))?@(.+)', line.strip())
            if m and 'github.com' in m.group(3):
                return m.group(2)
    except OSError:
        pass
    sys.exit('[x] 找不到 GitHub 凭据（~/.git-credentials 或 $GITHUB_TOKEN）')


TOKEN = token()


def api(path, method='GET', payload=None, raw=False, content_type=None,
        timeout=120, host=API, retries=4):
    """返回 (status, body)；status=None 表示网络层失败。

    自动重试瞬时错误：沙箱代理偶发 `Tunnel connection failed: 502 Bad Gateway`，
    以及 HTTP 5xx / 429。4xx（除 429）是确定性错误，立即返回不重试。
    """
    data = None
    if payload is not None:
        data = payload if raw else json.dumps(payload).encode('utf-8')
    last = '未执行'
    for attempt in range(1, retries + 1):
        req = urllib.request.Request(host + path, data=data, method=method)
        req.add_header('Authorization', 'token ' + TOKEN)
        req.add_header('Accept', 'application/vnd.github+json')
        req.add_header('User-Agent', UA)
        if data is not None:
            req.add_header('Content-Type', content_type or 'application/json')
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                body = r.read().decode('utf-8')
                return r.status, (json.loads(body) if body.strip() else {})
        except urllib.error.HTTPError as e:
            detail = e.read().decode('utf-8', 'replace')
            try:
                detail = json.loads(detail).get('message', detail)
            except Exception:
                pass
            if e.code >= 500 or e.code == 429:
                last = f'HTTP {e.code} {detail}'
            else:
                return e.code, detail
        except Exception as e:
            last = repr(e)
        if attempt < retries:
            sys.stderr.write(f'  … 第 {attempt} 次失败（{last}），{2 * attempt}s 后重试\n')
            time.sleep(2 * attempt)
    return None, last


def upload_asset(upload_url, name, blob, tries=8):
    """把发布资产上传到 uploads.github.com。

    ⚠️ 资产上传**只能**用 `uploads.github.com` 作为 host。
    换成 `api.github.com` 会 404 —— 那里的 `releases/{id}/assets` 只支持 GET（列资产）。
    所以这里**不做 host 回退**，只对它多试几次。

    沙箱代理对这个主机的拦截是**间歇性**的（`Tunnel connection failed: 502 Bad Gateway`）：
    同一时刻同类请求可能有的通有的不通，唯一可靠的办法就是重试。
    """
    url = f'{upload_url}?name={urllib.parse.quote(name)}'
    last = '未执行'
    for attempt in range(1, tries + 1):
        req = urllib.request.Request(url, data=blob, method='POST')
        req.add_header('Authorization', 'token ' + TOKEN)
        req.add_header('Accept', 'application/vnd.github+json')
        req.add_header('User-Agent', UA)
        req.add_header('Content-Type', 'application/java-archive')
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                return r.status, json.loads(r.read().decode('utf-8'))
        except urllib.error.HTTPError as e:
            detail = e.read().decode('utf-8', 'replace')[:200]
            last = f'HTTP {e.code} {detail}'
            if e.code != 429 and e.code < 500:
                return e.code, detail          # 确定性错误（如 422 同名资产），不重试
        except Exception as e:
            last = repr(e)
        if attempt < tries:
            print(f'  … 上传第 {attempt} 次失败（{last[:90]}），3s 后重试')
            time.sleep(3)
    return None, last


def git(*args, binary=False):
    # core.quotepath=false → 中文路径按原始 UTF-8 输出，不做 \346\255 式转义
    r = subprocess.run(['git', '-c', 'core.quotepath=false'] + list(args),
                       capture_output=True,
                       cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if r.returncode != 0:
        sys.exit('[x] git %s 失败: %s' % (' '.join(args),
                                         r.stderr.decode('utf-8', 'replace').strip()))
    return r.stdout if binary else r.stdout.decode('utf-8', 'replace').strip()


# ---------------------------------------------------------------- 差异计算

def remote_tree(sha):
    """远程 tree → {path: blob_sha}（已展开子目录，只留文件）"""
    st, body = api(f'/repos/{OWNER}/{REPO}/git/trees/{sha}?recursive=1')
    if st != 200:
        sys.exit(f'[x] 读取远程 tree 失败: {st} {body}')
    out = {}
    for e in body.get('tree', []):
        if e['type'] == 'blob':
            out[e['path']] = e['sha']
    return out, body.get('truncated', False)


def local_files():
    """所有「应存在于仓库」的文件 → {path: git_mode}（已应用 .gitignore）

    ⚠️ 必须过滤掉「索引里还在、磁盘上已删」的文件：它们仍会被 `git ls-files -s`
    列出来，但随后 `git hash-object` 会因文件不存在而失败
    （表现为 `fatal: could not open 'xxx' for reading`）。
    跳过之后，这类文件自然落进 `set(rtree) - local_set`，被当成「删除」提交上去。
    """
    modes = {}
    for line in git('ls-files', '-s').splitlines():
        parts = line.split(None, 3)
        if len(parts) == 4 and os.path.exists(os.path.join(ROOT, parts[3])):
            modes[parts[3]] = parts[0]
    for p in git('ls-files', '--others', '--exclude-standard').splitlines():
        if p.strip():
            modes.setdefault(p, '100644')
    return modes


def local_blob(path):
    """返回 (blob_sha, 规范化后的字节)。--path 让 autocrlf / .gitattributes 生效。"""
    sha = git('hash-object', '-w', '--path=' + path, path)
    content = git('cat-file', 'blob', sha, binary=True)
    return sha, content


def diff():
    st, ref = api(f'/repos/{OWNER}/{REPO}/git/ref/heads/{BRANCH}')
    if st != 200:
        sys.exit(f'[x] 读取远程分支失败: {st} {ref}')
    remote_commit = ref['object']['sha']
    st, commit = api(f'/repos/{OWNER}/{REPO}/git/commits/{remote_commit}')
    if st != 200:
        sys.exit(f'[x] 读取远程 commit 失败: {st} {commit}')
    rtree, truncated = remote_tree(commit['tree']['sha'])
    if truncated:
        print('[!] 警告：远程 tree 被截断，差异可能不完整')

    changes = []          # (path, sha_or_None, content_or_None, mode)
    local = local_files()
    local_set = set(local)
    for p in sorted(local):
        sha, content = local_blob(p)
        if rtree.get(p) != sha:
            changes.append((p, sha, content, local[p]))
    for p in sorted(set(rtree) - local_set):
        changes.append((p, None, None, None))   # 删除
    return remote_commit, changes, len(local), len(rtree), rtree


# ---------------------------------------------------------------- 命令

def cmd_status():
    remote_commit, changes, n_local, n_remote, rtree = diff()
    print(f'远程 main : {remote_commit[:12]}（{n_remote} 个文件）')
    print(f'本地      : {n_local} 个文件')
    if not changes:
        print('\n[OK] 没有差异，远程已是最新。')
        return
    print(f'\n共 {len(changes)} 处差异：')
    for p, sha, _, _ in changes:
        if sha is None:
            kind = '删除'
        else:
            kind = '修改' if p in rtree else '新增'
        print(f'  {kind}  {p}')


def cmd_sync(message=None, do_release=None):
    remote_commit, changes, n_local, n_remote, _ = diff()
    if not changes:
        print('[OK] 没有差异，无需提交。')
    else:
        print(f'远程 main {remote_commit[:12]} → 待提交 {len(changes)} 处差异')
        entries = []
        for p, sha, _, mode in changes:
            if sha is None:
                entries.append({'path': p, 'mode': '100644', 'type': 'blob', 'sha': None})
            else:
                entries.append({'path': p, 'mode': mode or '100644',
                                'type': 'blob', 'sha': sha})

        st, ref = api(f'/repos/{OWNER}/{REPO}/git/ref/heads/{BRANCH}')
        base_commit = ref['object']['sha']
        st, bcommit = api(f'/repos/{OWNER}/{REPO}/git/commits/{base_commit}')
        base_tree = bcommit['tree']['sha']

        print('上传 blob…')
        for p, sha, content, _ in changes:
            if sha is None:
                continue
            st, r = api(f'/repos/{OWNER}/{REPO}/git/blobs', 'POST', {
                'content': base64.b64encode(content).decode('ascii'),
                'encoding': 'base64',
            })
            if st != 201:
                sys.exit(f'[x] 上传 blob 失败 {p}: {st} {r}')
            if r['sha'] != sha:
                sys.exit(f'[x] blob sha 不匹配 {p}: 本地 {sha} / 远程返回 {r["sha"]}')

        print('创建 tree…')
        st, tree = api(f'/repos/{OWNER}/{REPO}/git/trees', 'POST',
                       {'base_tree': base_tree, 'tree': entries})
        if st != 201:
            sys.exit(f'[x] 创建 tree 失败: {st} {tree}')

        msg = message or ('同步本地改动（%d 个文件）' % len(changes))
        print('创建 commit…')
        st, c = api(f'/repos/{OWNER}/{REPO}/git/commits', 'POST', {
            'message': msg, 'tree': tree['sha'],
            'parents': [base_commit],
        })
        if st != 201:
            sys.exit(f'[x] 创建 commit 失败: {st} {c}')

        st, r = api(f'/repos/{OWNER}/{REPO}/git/refs/heads/{BRANCH}', 'PATCH',
                    {'sha': c['sha']})
        if st != 200:
            sys.exit(f'[x] 更新分支失败: {st} {r}')
        print(f'[OK] 已提交并推送到 main：{base_commit[:8]} → {c["sha"][:8]}')
        print('     ' + c['html_url'])
        print('     注意：提交由 API 创建，本地 HEAD 不会前进（缺该 commit 对象），')
        print('     所以本地 git status 可能仍显示这些文件「有改动」—— 以本脚本 status 为准。')

    if do_release is not None:
        cmd_release(do_release)


def cmd_release(version, notes=None, jar=None):
    version = version.lstrip('v')
    tag = 'v' + version
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if not jar:
        # 2026-10-17 起产物名改为 wuling-empire-v<版本>-beta.jar；
        # 找不到就退回同名 glob（兼容带 test 后缀的测试构建与旧命名）。
        libs = os.path.join(root, 'build', 'libs')
        jar = os.path.join(libs, f'wuling-empire-{tag}-beta.jar')
        if not os.path.isfile(jar):
            hits = sorted(glob.glob(os.path.join(libs, f'wuling-empire-{tag}*.jar')))
            if hits:
                jar = hits[-1]
            else:
                legacy = os.path.join(libs, f'Wuling-Empire-{tag}.jar')
                if os.path.isfile(legacy):
                    jar = legacy
    if not jar or not os.path.isfile(jar):
        sys.exit(f'[x] 找不到产物：{jar}（先构建，或显式指定 --jar）')
    size = os.path.getsize(jar)
    print(f'产物 {os.path.basename(jar)}  {size:,} B')

    st, ref = api(f'/repos/{OWNER}/{REPO}/git/ref/heads/{BRANCH}')
    head = ref['object']['sha']

    # tag（已存在则复用）
    st, existing = api(f'/repos/{OWNER}/{REPO}/git/ref/tags/{tag}')
    if st == 200:
        print(f'tag {tag} 已存在，复用')
    else:
        st, tobj = api(f'/repos/{OWNER}/{REPO}/git/tags', 'POST', {
            'tag': tag, 'message': f'WuLing Empire {version}',
            'object': head, 'type': 'commit',
        })
        if st != 201:
            sys.exit(f'[x] 创建 tag 失败: {st} {tobj}')
        st, r = api(f'/repos/{OWNER}/{REPO}/git/refs', 'POST',
                    {'ref': f'refs/tags/{tag}', 'sha': tobj['sha']})
        if st != 201:
            sys.exit(f'[x] 创建 tag ref 失败: {st} {r}')
        print(f'tag {tag} 已创建 → {head[:8]}')

    # release（已存在则复用）
    st, existing = api(f'/repos/{OWNER}/{REPO}/releases/tags/{tag}')
    if st == 200:
        release = existing
        print(f'Release {tag} 已存在，复用')
    else:
        st, release = api(f'/repos/{OWNER}/{REPO}/releases', 'POST', {
            'tag_name': tag, 'name': f'WuLing Empire {version}',
            'body': notes or f'WuLing Empire {version}', 'draft': False,
            'prerelease': False,
        })
        if st != 201:
            sys.exit(f'[x] 创建 Release 失败: {st} {release}')
        print('Release 已创建 → ' + release['html_url'])

    name = os.path.basename(jar)
    for a in release.get('assets', []):
        if a['name'] == name:
            api(f'/repos/{OWNER}/{REPO}/releases/assets/{a["id"]}', 'DELETE')
            print(f'旧资产 {name} 已删除（覆盖上传）')

    upload = release['upload_url'].split('{')[0]
    with open(jar, 'rb') as f:
        blob = f.read()
    st, asset = upload_asset(upload, name, blob)
    if st != 201:
        sys.exit(f'[x] 上传资产失败: {st} {asset}')
    print(f'[OK] 资产已上传：{asset["name"]}  {asset["size"]:,} B')
    print('     下载 ' + asset['browser_download_url'])


def main():
    argv = sys.argv[1:]
    if not argv or argv[0] in ('-h', '--help', 'help'):
        print(__doc__)
        return
    cmd, rest = argv[0], argv[1:]

    def opt(flag, default=None):
        if flag in rest:
            return rest[rest.index(flag) + 1]
        return default

    if cmd == 'status':
        cmd_status()
    elif cmd == 'sync':
        cmd_sync(opt('-m') or opt('--message'), opt('--release'))
    elif cmd == 'release':
        v = rest[0] if rest and not rest[0].startswith('-') else None
        if not v:
            sys.exit('[x] 用法：release <版本号>，例如 release 0.2.29')
        cmd_release(v, opt('-n') or opt('--notes'), opt('--jar'))
    else:
        sys.exit(f'[x] 未知命令 {cmd}（可用：status / sync / release）')


if __name__ == '__main__':
    main()
