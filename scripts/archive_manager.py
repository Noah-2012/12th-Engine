import os
import struct
import tkinter as tk
from tkinter import filedialog, messagebox, ttk


def xor_obfuscate(data, key):
    """Obfuscates or deobfuscates byte data using a rolling XOR with the provided key."""
    if not key:
        return data
    key_len = len(key)
    return bytes([b ^ key[i % key_len] for i, b in enumerate(data)])


def format_size(size):
    """Formats a byte size into a human-readable string."""
    for unit in ["B", "KB", "MB", "GB"]:
        if size < 1024.0:
            return f"{size:.2f} {unit}"
        size /= 1024.0
    return f"{size:.2f} TB"


class ArchiveManagerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("12th Engine - Archive Manager")
        self.root.geometry("800x500")

        # State
        self.current_file_path = None
        self.magic_number = b"TWA1"  # Default to archive
        self.files = []  # List of dicts: {'name': str, 'data': bytes}
        self.current_key = None

        self.setup_ui()
        self.update_ui_state()

    def setup_ui(self):
        # --- Menu Bar ---
        menubar = tk.Menu(self.root)

        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(
            label="New Archive (.twa)", command=lambda: self.new_archive(b"TWA1")
        )
        file_menu.add_command(
            label="New Model (.twm)", command=lambda: self.new_archive(b"TWM1")
        )
        file_menu.add_separator()
        file_menu.add_command(label="Open Archive...", command=self.open_archive)
        file_menu.add_command(label="Save", command=self.save_archive)
        file_menu.add_command(label="Save As...", command=self.save_archive_as)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.quit)
        menubar.add_cascade(label="File", menu=file_menu)

        action_menu = tk.Menu(menubar, tearoff=0)
        action_menu.add_command(label="Add File(s)...", command=self.add_files)
        action_menu.add_command(label="Add Directory...", command=self.add_directory)
        action_menu.add_separator()
        action_menu.add_command(
            label="Extract Selected...", command=self.extract_selected
        )
        action_menu.add_command(label="Remove Selected", command=self.remove_selected)
        menubar.add_cascade(label="Actions", menu=action_menu)

        self.root.config(menu=menubar)

        # --- Main Layout ---
        main_frame = ttk.Frame(self.root, padding=10)
        main_frame.pack(fill=tk.BOTH, expand=True)

        # Top toolbar
        toolbar = ttk.Frame(main_frame)
        toolbar.pack(fill=tk.X, pady=(0, 10))

        ttk.Button(toolbar, text="Open", command=self.open_archive).pack(
            side=tk.LEFT, padx=(0, 5)
        )
        ttk.Button(toolbar, text="Save", command=self.save_archive).pack(
            side=tk.LEFT, padx=(0, 5)
        )
        ttk.Button(toolbar, text="Add Files", command=self.add_files).pack(
            side=tk.LEFT, padx=(5, 5)
        )
        ttk.Button(toolbar, text="Extract", command=self.extract_selected).pack(
            side=tk.LEFT, padx=(5, 5)
        )
        ttk.Button(toolbar, text="Remove", command=self.remove_selected).pack(
            side=tk.LEFT, padx=(5, 5)
        )

        # File list
        columns = ("Name", "Size")
        self.tree = ttk.Treeview(
            main_frame, columns=columns, show="headings", selectmode="extended"
        )
        self.tree.heading("Name", text="File Name", anchor=tk.W)
        self.tree.heading("Size", text="Size", anchor=tk.E)
        self.tree.column("Name", width=500, anchor=tk.W)
        self.tree.column("Size", width=100, anchor=tk.E)

        scrollbar = ttk.Scrollbar(
            main_frame, orient=tk.VERTICAL, command=self.tree.yview
        )
        self.tree.configure(yscroll=scrollbar.set)

        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        # --- Status Bar ---
        self.status_var = tk.StringVar()
        self.status_var.set("Ready. No archive loaded.")
        status_bar = ttk.Label(
            self.root,
            textvariable=self.status_var,
            relief=tk.SUNKEN,
            anchor=tk.W,
            padding=2,
        )
        status_bar.pack(side=tk.BOTTOM, fill=tk.X)

    def update_ui_state(self):
        self.tree.delete(*self.tree.get_children())
        total_size = 0

        for i, file_obj in enumerate(self.files):
            size = len(file_obj["data"])
            total_size += size
            self.tree.insert(
                "", tk.END, iid=str(i), values=(file_obj["name"], format_size(size))
            )

        file_type = "Archive (.twa)" if self.magic_number == b"TWA1" else "Model (.twm)"

        if self.current_file_path:
            status = f"Loaded {file_type}: {self.current_file_path} | {len(self.files)} files | Total: {format_size(total_size)}"
            if self.current_key:
                status += f" | Obfuscation Key: {self.current_key.hex()}"
        else:
            status = f"New {file_type} | {len(self.files)} files | Total: {format_size(total_size)}"

        self.status_var.set(status)

    def new_archive(self, magic):
        self.current_file_path = None
        self.magic_number = magic
        self.files = []
        self.current_key = None
        self.update_ui_state()

    def open_archive(self):
        filepath = filedialog.askopenfilename(
            title="Open Twelfth Archive",
            filetypes=[("Twelfth Archives", "*.twa *.twm"), ("All Files", "*.*")],
        )
        if not filepath:
            return

        try:
            with open(filepath, "rb") as f:
                # Read 12-byte obfuscation key
                key = f.read(12)
                if len(key) != 12:
                    raise ValueError(
                        "File is too small to contain a valid obfuscation key."
                    )

                # Read rest of file and deobfuscate
                obfuscated_payload = f.read()
                payload = xor_obfuscate(obfuscated_payload, key)

                # Parse Header
                magic = payload[0:4]
                if magic not in (b"TWA1", b"TWM1"):
                    raise ValueError(
                        f"Invalid magic number: {magic}. Expected TWA1 or TWM1."
                    )

                file_count = struct.unpack("<I", payload[4:8])[0]

                parsed_files = []
                offset = 8

                for i in range(file_count):
                    # Name Length
                    name_len = struct.unpack("<H", payload[offset : offset + 2])[0]
                    offset += 2

                    # Name
                    name = payload[offset : offset + name_len].decode("utf-8")
                    offset += name_len

                    # Data Length
                    data_len = struct.unpack("<I", payload[offset : offset + 4])[0]
                    offset += 4

                    # Data
                    data = payload[offset : offset + data_len]
                    offset += data_len

                    parsed_files.append({"name": name, "data": data})

            self.current_file_path = filepath
            self.magic_number = magic
            self.files = parsed_files
            self.current_key = key
            self.update_ui_state()

        except Exception as e:
            messagebox.showerror(
                "Error Opening Archive", f"Failed to open '{filepath}':\n\n{str(e)}"
            )

    def save_archive(self):
        if not self.current_file_path:
            self.save_archive_as()
        else:
            self._write_archive(self.current_file_path)

    def save_archive_as(self):
        default_ext = ".twa" if self.magic_number == b"TWA1" else ".twm"
        filetypes = (
            [("Twelfth Archive", "*.twa")]
            if default_ext == ".twa"
            else [("Twelfth Model", "*.twm")]
        )
        filetypes.append(("All Files", "*.*"))

        filepath = filedialog.asksaveasfilename(
            title="Save Archive As...",
            defaultextension=default_ext,
            filetypes=filetypes,
        )
        if filepath:
            self.current_file_path = filepath
            self._write_archive(filepath)

    def _write_archive(self, filepath):
        try:
            payload = bytearray()

            # Write Magic Number
            payload.extend(self.magic_number)

            # Write File Count
            payload.extend(struct.pack("<I", len(self.files)))

            for file_obj in self.files:
                # Name Length & Name
                name_bytes = file_obj["name"].encode("utf-8")
                payload.extend(struct.pack("<H", len(name_bytes)))
                payload.extend(name_bytes)

                # Data Length & Data
                payload.extend(struct.pack("<I", len(file_obj["data"])))
                payload.extend(file_obj["data"])

            # Generate new 12-byte obfuscation key every time we save to ensure strong obfuscation
            new_key = os.urandom(12)
            obfuscated_payload = xor_obfuscate(payload, new_key)

            with open(filepath, "wb") as f:
                f.write(new_key)
                f.write(obfuscated_payload)

            self.current_key = new_key
            self.update_ui_state()
            messagebox.showinfo(
                "Success", f"Archive successfully obfuscated and saved to:\n{filepath}"
            )

        except Exception as e:
            messagebox.showerror(
                "Error Saving Archive", f"Failed to save archive:\n\n{str(e)}"
            )

    def add_files(self):
        filepaths = filedialog.askopenfilenames(title="Select Files to Add")
        if not filepaths:
            return

        for path in filepaths:
            try:
                with open(path, "rb") as f:
                    data = f.read()
                name = os.path.basename(path)

                # Check for overwrites
                for existing in self.files:
                    if existing["name"] == name:
                        existing["data"] = data
                        break
                else:
                    self.files.append({"name": name, "data": data})
            except Exception as e:
                messagebox.showerror(
                    "Error", f"Failed to read file '{path}':\n{str(e)}"
                )

        self.update_ui_state()

    def add_directory(self):
        dirpath = filedialog.askdirectory(title="Select Directory to Add")
        if not dirpath:
            return

        base_dir = os.path.dirname(dirpath)

        for root, _, filenames in os.walk(dirpath):
            for filename in filenames:
                file_path = os.path.join(root, filename)
                try:
                    with open(file_path, "rb") as f:
                        data = f.read()

                    # Calculate relative path
                    arc_name = os.path.relpath(file_path, base_dir)
                    arc_name = arc_name.replace("\\", "/")  # Standardize slashes

                    # Check for overwrites
                    for existing in self.files:
                        if existing["name"] == arc_name:
                            existing["data"] = data
                            break
                    else:
                        self.files.append({"name": arc_name, "data": data})
                except Exception as e:
                    messagebox.showwarning(
                        "Warning", f"Failed to read file '{file_path}':\n{str(e)}"
                    )

        self.update_ui_state()

    def remove_selected(self):
        selected = self.tree.selection()
        if not selected:
            return

        # Delete in reverse order to maintain indices
        indices = sorted([int(x) for x in selected], reverse=True)
        for idx in indices:
            del self.files[idx]

        self.update_ui_state()

    def extract_selected(self):
        selected = self.tree.selection()
        if not selected:
            messagebox.showinfo(
                "Extract", "Please select at least one file to extract."
            )
            return

        out_dir = filedialog.askdirectory(title="Select Extraction Destination")
        if not out_dir:
            return

        indices = [int(x) for x in selected]
        extracted = 0

        for idx in indices:
            file_obj = self.files[idx]
            # Handle files that have subdirectories in their names
            out_path = os.path.join(out_dir, file_obj["name"].replace("/", os.sep))

            try:
                os.makedirs(os.path.dirname(out_path), exist_ok=True)
                with open(out_path, "wb") as f:
                    f.write(file_obj["data"])
                extracted += 1
            except Exception as e:
                messagebox.showerror(
                    "Extraction Error",
                    f"Failed to extract '{file_obj['name']}':\n{str(e)}",
                )

        if extracted > 0:
            messagebox.showinfo(
                "Success", f"Successfully extracted {extracted} file(s)."
            )


if __name__ == "__main__":
    root = tk.Tk()
    app = ArchiveManagerApp(root)
    root.mainloop()
