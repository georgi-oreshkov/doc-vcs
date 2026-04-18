import { useState, useRef } from "react";
import {
    Modal,
    ModalContent,
    ModalHeader,
    ModalBody,
    ModalFooter,
    Button,
    Input
} from "@heroui/react";
import { UploadCloud, FileText, X } from "lucide-react";

export default function NewDocumentModal({ isOpen, onOpenChange }) {
    const [title, setTitle] = useState("");
    const [file, setFile] = useState(null);
    const fileInputRef = useRef(null);

    // Function to handle file selection
    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            setFile(e.target.files[0]);
        }
    };

    // Function to handle saving the document (placeholder for now)
    const handleSave = async (onClose) => {
        console.log("Saving document:", { title, file });
        // To-Do: Implement actual upload logic here (e.g., API call)

        /*
        setTitle("");
        setFile(null);
        onClose();
        */

        //----------------------------------------------------------------------------
        if (!file) {
            console.error("No file selected");
            return;
        }

        try {
            //console.log("Starting upload...");

            // 2. Request presigned URL from backend
            const res = await fetch(
                `http://localhost:8080/presign?fileName=${file.name}`//change to presign
            );

            if (!res.ok) {
                throw new Error("Failed to get presigned URL");
            }

            const uploadUrl = await res.text();
            console.log("Presigned URL:", uploadUrl);

            // 3. Upload file directly to S3
            const uploadRes = await fetch(uploadUrl, {
                method: "PUT",
                body: file,
            });

            if (!uploadRes.ok) {
                throw new Error("Upload to S3 failed");
            }

            console.log("Upload successful!");

            // 4. Reset UI
            setTitle("");
            setFile(null);
            onClose();

        } catch (error) {
            console.error("Upload error:", error);
        }
        //------------------------------------------------------------------------=
    };

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="blur" classNames={{
            base: "bg-zinc-950 border border-zinc-800",
            header: "border-b border-zinc-800",
            footer: "border-t border-zinc-800",
        }}>
            <ModalContent>
                {(onClose) => (
                    <>
                        <ModalHeader className="flex flex-col gap-1 text-white">Upload New Document</ModalHeader>
                        <ModalBody className="py-6 gap-6">
                            <div>
                                <p className="text-sm text-zinc-400 mb-2">Document Title</p>
                                <Input
                                    placeholder="e.g., Q4 Marketing Strategy"
                                    variant="bordered"
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                    classNames={{
                                        input: "text-white text-base"
                                    }}
                                />
                            </div>

                            {/* Upload Zone */}
                            <div>
                                <p className="text-sm text-zinc-400 mb-2">Upload File</p>
                                {!file ? (
                                    <div
                                        onClick={() => fileInputRef.current?.click()}
                                        className="border-2 border-dashed border-zinc-700 hover:border-zinc-500 hover:bg-zinc-900/50 transition-colors rounded-xl p-8 flex flex-col items-center justify-center cursor-pointer gap-2"
                                    >
                                        <UploadCloud className="text-zinc-500" size={32} />
                                        <p className="text-sm text-zinc-300">Click to browse or drag and drop</p>
                                        <p className="text-xs text-zinc-500">PDF, DOCX, XLSX up to 50MB</p>
                                    </div>
                                ) : (
                                    <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4 flex items-center justify-between">
                                        <div className="flex items-center gap-3 overflow-hidden">
                                            <FileText className="text-blue-500 shrink-0" size={24} />
                                            <div className="truncate">
                                                <p className="text-sm text-white truncate">{file.name}</p>
                                                <p className="text-xs text-zinc-500">{(file.size / 1024 / 1024).toFixed(2)} MB</p>
                                            </div>
                                        </div>
                                        <Button isIconOnly variant="light" size="sm" onClick={() => setFile(null)}>
                                            <X className="text-zinc-400 hover:text-red-400" size={18} />
                                        </Button>
                                    </div>
                                )}
                                {/* Hidden File Input */}
                                <input
                                    type="file"
                                    ref={fileInputRef}
                                    className="hidden"
                                    onChange={handleFileChange}
                                />
                            </div>

                        </ModalBody>
                        <ModalFooter>
                            <Button color="danger" variant="light"  onPress={onClose}>
                                Cancel
                            </Button>
                            <Button 
                                className="bg-lime-600 text-black font-bold hover:bg-lime-500 disabled:bg-zinc-800 disabled:text-zinc-600 transition-colors"
                                onPress={() => handleSave(onClose)} isDisabled={!title || !file}
                            >
                                Upload Document
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}