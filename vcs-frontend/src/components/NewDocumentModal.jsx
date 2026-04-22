import { useState, useRef } from "react";
import {
    Modal,
    ModalContent,
    ModalHeader,
    ModalBody,
    ModalFooter,
    Button,
    Input,
    Select,
    SelectItem
} from "@heroui/react";
import { UploadCloud, FileText, X } from "lucide-react";
import { createDocument } from '../api/documentsApi';
import { uploadFileToS3 } from '../api/versionsApi';
import { useOrg } from '../context/OrgContext';

export default function NewDocumentModal({ isOpen, onOpenChange, onSave, isSaving, categories = [] }) {
    const [title, setTitle] = useState("");
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [selectedCategory, setSelectedCategory] = useState(new Set([]));
    const [isDragging, setIsDragging] = useState(false); // Добавен стейт за влачене
    const fileInputRef = useRef(null);
    const { selectedOrg } = useOrg();

    // Хващане на файл чрез кликане
    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            setFile(e.target.files[0]);
        }
    };

    // БРОНИРАН DRAG & DROP
    const handleDragEnter = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(true);
    };

    const handleDragLeave = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(false);
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(true);
    };

    const handleDrop = (e) => {
        e.preventDefault();
        e.stopPropagation(); // Спира отварянето на файла в браузъра
        setIsDragging(false);

        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            setFile(e.dataTransfer.files[0]);
        }
    };

    const resetForm = () => {
        setTitle("");
        setFile(null);
        setSelectedCategory(new Set([]));
        setIsDragging(false);
    };

    // Функция за handle saving
    const handleSave = async (onClose) => {
        if (!file || !title) {
            console.error("Missing title or file");
            return;
        }

        if (!selectedOrg?.id) {
            console.error("No organization selected");
            return;
        }

        const categoryId = Array.from(selectedCategory)[0] ?? undefined;
        const docData = { name: title, ...(categoryId ? { category_id: categoryId } : {}) };

        try {
            setUploading(true);

            if (onSave) {
                await onSave(docData, file, onClose);
                resetForm();
            } else {
                const response = await createDocument(selectedOrg.id, docData);

                if (!response.upload_url) {
                    throw new Error("Failed to get presigned URL");
                }

                await uploadFileToS3(response.upload_url, file);

                resetForm();
                onClose();
            }
        } catch (error) {
            console.error("Upload error:", error);
        } finally {
            setUploading(false);
        }
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
                                        inputWrapper: "border-zinc-800 bg-zinc-900/50 hover:border-zinc-700 focus-within:!border-lime-500",
                                        input: "text-white"
                                    }}
                                />
                            </div>

                            {categories.length > 0 && (
                                <div>
                                    <p className="text-sm text-zinc-400 mb-2">Category <span className="text-zinc-600">(optional)</span></p>
                                    <Select
                                        isClearable
                                        variant="bordered"
                                        placeholder="Select a category"
                                        selectedKeys={selectedCategory}
                                        onSelectionChange={setSelectedCategory}
                                        classNames={{
                                            trigger: "border-zinc-800 bg-zinc-900/50 hover:border-zinc-700",
                                            value: "text-white"
                                        }}
                                    >
                                        {categories.map((c) => (
                                            <SelectItem key={c.id}>{c.name}</SelectItem>
                                        ))}
                                    </Select>
                                </div>
                            )}

                            {/* Upload Zone */}
                            <div>
                                <p className="text-sm text-zinc-400 mb-2">Upload File</p>
                                {!file ? (
                                    <div
                                        onClick={() => fileInputRef.current?.click()}
                                        onDragEnter={handleDragEnter}
                                        onDragOver={handleDragOver}
                                        onDragLeave={handleDragLeave}
                                        onDrop={handleDrop}
                                        className={`border-2 border-dashed transition-colors rounded-xl p-8 flex flex-col items-center justify-center cursor-pointer gap-2 ${
                                            isDragging 
                                                ? 'border-lime-500 bg-zinc-900/80' 
                                                : 'border-zinc-700 hover:border-zinc-500 hover:bg-zinc-900/50'
                                        }`}
                                    >
                                        <UploadCloud className={`${isDragging ? 'text-lime-500' : 'text-zinc-500'}`} size={32} />
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
                                        <Button isIconOnly variant="light" size="sm" onPress={() => setFile(null)}>
                                            <X className="text-zinc-400 hover:text-red-400" size={18} />
                                        </Button>
                                    </div>
                                )}
                                <input
                                    type="file"
                                    ref={fileInputRef}
                                    className="hidden"
                                    onChange={handleFileChange}
                                />
                            </div>

                        </ModalBody>
                        <ModalFooter>
                            <Button color="danger" variant="light" onPress={() => { resetForm(); onClose(); }}>
                                Cancel
                            </Button>
                            <Button
                                className="bg-lime-600 text-black font-bold hover:bg-lime-500 disabled:bg-zinc-800 disabled:text-zinc-600 transition-colors"
                                onPress={() => handleSave(onClose)}
                                isDisabled={!title || !file}
                                isLoading={uploading || isSaving}
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