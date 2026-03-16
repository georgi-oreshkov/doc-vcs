import { useState, useMemo } from 'react';
import { Input, Select, SelectItem } from "@heroui/react";
import { Search, Filter, User } from 'lucide-react';

// 1. ТОВА Е ТВОЯТ CUSTOM HOOK - Той държи логиката
export function useDocumentFilters(docs) {
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedAuthor, setSelectedAuthor] = useState(new Set([]));
    const [selectedStatus, setSelectedStatus] = useState(new Set([]));

    const filteredDocuments = useMemo(() => {
        return docs.filter((doc) => {
            const authorFilterArray = Array.from(selectedAuthor);
            const statusFilterArray = Array.from(selectedStatus);

            const matchesAuthor = authorFilterArray.length === 0 || authorFilterArray.includes(doc.author);
            const matchesStatus = statusFilterArray.length === 0 || statusFilterArray.includes(doc.status);
            const matchesSearch = doc.title.toLowerCase().includes(searchQuery.toLowerCase());

            return matchesAuthor && matchesStatus && matchesSearch;
        });
    }, [docs, selectedAuthor, selectedStatus, searchQuery]);

    // Връщаме всичко, което ще трябва на главния компонент
    return {
        searchQuery, setSearchQuery,
        selectedAuthor, setSelectedAuthor,
        selectedStatus, setSelectedStatus,
        filteredDocuments
    };
}

export default function DocumentsFilter({
    searchQuery,
    setSearchQuery,
    selectedAuthor,
    setSelectedAuthor,
    selectedStatus,
    setSelectedStatus
}) {
    return (
        <div className="flex gap-4 mb-8 bg-zinc-900/50 p-4 rounded-xl border border-zinc-800/80">
            <Input
                className="flex-1" variant="bordered"
                startContent={<Search size={18} className="text-zinc-500" />}
                placeholder="Search documents by title..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
            />

            <Select
                isClearable
                variant="bordered"
                className="max-w-xs"
                placeholder="Filter by Author"
                startContent={<User size={18} className="text-zinc-500" />}
                selectedKeys={selectedAuthor}
                onSelectionChange={setSelectedAuthor}
            >
                <SelectItem key="You">You</SelectItem>
                <SelectItem key="Alice Smith">Alice Smith</SelectItem>
                <SelectItem key="Bob Jones">Bob Jones</SelectItem>
            </Select>

            <Select
                isClearable
                variant="bordered"
                className="max-w-xs"
                placeholder="Filter by Status"
                startContent={<Filter size={18} className="text-zinc-500" />}
                selectedKeys={selectedStatus}
                onSelectionChange={setSelectedStatus}
            >
                <SelectItem key="Approved">Approved</SelectItem>
                <SelectItem key="In Review">In Review</SelectItem>
                <SelectItem key="Draft">Draft</SelectItem>
            </Select>
        </div>
    );
}