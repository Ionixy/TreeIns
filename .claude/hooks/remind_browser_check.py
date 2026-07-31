import sys, json

data = json.load(sys.stdin)
path = (data.get("tool_input") or {}).get("file_path") or (data.get("tool_response") or {}).get("filePath") or ""

if path.replace("\\", "/").endswith("TreeIns.html"):
    print(json.dumps({
        "systemMessage": "Напоминание: открой TreeIns.html в браузере (Claude Browser) и проверь изменения вручную, прежде чем считать задачу готовой."
    }))
