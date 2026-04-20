import re
import sys
import requests
import urllib.parse
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

# =========================
# CONFIG
# =========================
RETRIES = 3

# =========================
# AI CALL
# =========================
def call_model(full_file, snippet, name, kind):
    prompt = f"""
Generate ONLY JavaDoc for this {kind}: {name}

Rules:
- return only /** ... */
- concise but helpful
- use full file context

FILE:
{full_file}

SNIPPET:
{snippet}
"""

    url = "https://text.pollinations.ai/" + urllib.parse.quote(prompt)

    for _ in range(RETRIES):
        try:
            r = requests.get(url, timeout=60)

            if r.status_code != 200:
                continue

            text = r.text.strip()

            if text.startswith("/**"):
                return text

        except:
            pass

    return None


# =========================
# REGEX
# =========================
METHOD_REGEX = re.compile(
    r"(public|private|protected)\s+(static\s+)?[\w<>\[\]]+\s+(\w+)\s*\(([^)]*)\)\s*\{"
)

CLASS_REGEX = re.compile(r"(public\s+class\s+(\w+))")


# =========================
# HELPERS
# =========================
def has_javadoc_above(code, index):
    # stricter: must end immediately above
    before = code[:index].rstrip()

    # last comment block check
    return re.search(r"/\*\*[\s\S]*?\*/\s*$", before) is not None


def get_indent(code, index):
    line_start = code.rfind("\n", 0, index) + 1
    indent = ""
    while line_start < len(code) and code[line_start] in (" ", "\t"):
        indent += code[line_start]
        line_start += 1
    return indent


def indent_block(block, indent):
    return "\n".join(indent + l for l in block.split("\n")) + "\n"


# =========================
# PARSING
# =========================
def find_class(code):
    m = CLASS_REGEX.search(code)
    if not m:
        return None
    return {"name": m.group(2), "start": m.start(), "snippet": m.group(1)}


def find_methods(code):
    methods = []

    lines = code.split("\n")

    i = 0
    while i < len(lines):

        line = lines[i].strip()

        # detect method start (very loose)
        if ("public" in line or "private" in line or "protected" in line):

            start_index = i
            signature = line

            # gather full signature across multiple lines
            while "{" not in lines[i]:
                i += 1
                if i >= len(lines):
                    break
                signature += " " + lines[i].strip()

            if i >= len(lines):
                break

            if "(" in signature and ")" in signature:

                name_match = re.search(r"(\w+)\s*\(", signature)
                if not name_match:
                    i += 1
                    continue

                name = name_match.group(1)

                # now find full method body via braces
                brace = 0
                start_char_index = code.find(lines[start_index])

                j = start_char_index

                while j < len(code):
                    if code[j] == "{":
                        brace += 1
                    elif code[j] == "}":
                        brace -= 1
                        if brace == 0:
                            methods.append({
                                "name": name,
                                "start": start_char_index,
                                "snippet": code[start_char_index:j+1]
                            })
                            break
                    j += 1

        i += 1

    return methods


# =========================
# PROCESS FILE
# =========================
def process_file(path: Path):
    try:
        code = path.read_text(encoding="utf-8")

        original = code  # debug

        # CLASS
        cls = find_class(code)
        if cls and not has_javadoc_above(code, cls["start"]):
            jd = call_model(code, cls["snippet"], cls["name"], "class")
            if jd:
                indent = get_indent(code, cls["start"])
                code = code[:cls["start"]] + indent_block(jd, indent) + code[cls["start"]:]

        # METHODS
        offset = 0
        methods = find_methods(code)

        for m in methods:
            start = m["start"] + offset

            if has_javadoc_above(code, start):
                continue

            jd = call_model(code, m["snippet"], m["name"], "method")
            if not jd:
                continue

            indent = get_indent(code, start)
            jd = indent_block(jd, indent)

            code = code[:start] + jd + code[start:]
            offset += len(jd)

        # only write if changed
        if code != original:
            path.write_text(code, encoding="utf-8")
            return f"✓ {path.name} (modified)"
        else:
            return f"- {path.name} (no changes)"

    except Exception as e:
        return f"✗ {path.name}: {e}"


# =========================
# MAIN
# =========================
def main():
    if len(sys.argv) < 3:
        print("Usage: python javadoc_folder.py <folder> <subfolders:true/false> [workers]")
        return

    folder = Path(sys.argv[1])
    recursive = sys.argv[2].lower() == "true"
    workers = int(sys.argv[3]) if len(sys.argv) >= 4 else 6

    if not folder.exists():
        print("Folder not found:", folder)
        return

    files = list(folder.rglob("*.java") if recursive else folder.glob("*.java"))

    print(f"Found {len(files)} files")
    print(f"Workers: {workers}")
    print(f"MODE: IN-PLACE editing\n")

    with ThreadPoolExecutor(max_workers=workers) as ex:
        futures = [ex.submit(process_file, f) for f in files]

        for f in as_completed(futures):
            print(f.result())

    print("\n🎉 Done")

if __name__ == "__main__":
    main()