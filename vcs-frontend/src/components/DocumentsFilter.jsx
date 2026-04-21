import { useState, useMemo } from 'react';
import { Input, Select, SelectItem } from "@heroui/react";
import { Search, Filter, User } from 'lucide-react';

// 1. This hook encapsulates all the filter logic and state management for documents
export function useDocumentFilters(docs) {
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedAuthor, setSelectedAuthor] = useState(new Set([]));
    const [selectedStatus, setSelectedStatus] = useState(new Set([]));

    const filteredDocuments = useMemo(() => {
        return docs.filter((doc) => {
            const authorFilterArray = Array.from(selectedAuthor);
            const statusFilterArray = Array.from(selectedStatus);

            // Compare by authorId (UUID) when a filter is applied
            const matchesAuthor = authorFilterArray.length === 0 || authorFilterArray.includes(doc.authorId);
            const matchesStatus = statusFilterArray.length === 0 || statusFilterArray.includes(doc.status);
            const matchesSearch = doc.title.toLowerCase().includes(searchQuery.toLowerCase());

            return matchesAuthor && matchesStatus && matchesSearch;
        });
    }, [docs, selectedAuthor, selectedStatus, searchQuery]);

    // Return all states and setters so that the component can use them
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
    setSelectedStatus,
    orgUsers = [],
}) {
    return (
        <div className="flex flex-col lg:flex-row gap-4 mb-8 bg-zinc-900/50 p-4 rounded-xl border border-zinc-800/80">
            
            <Input
                className="w-full lg:flex-1" 
                variant="bordered"
                startContent={<Search size={18} className="text-zinc-500" />}
                placeholder="Search documents by title..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
            />

            <div className="flex flex-col sm:flex-row gap-4 w-full lg:w-auto">
                {orgUsers.length > 0 && (
                    <Select
                        isClearable
                        variant="bordered"
                        className="w-full sm:w-1/2 lg:w-48 xl:w-64"
                        placeholder="Filter by Author"
                        startContent={<User size={18} className="text-zinc-500" />}
                        selectedKeys={selectedAuthor}
                        onSelectionChange={setSelectedAuthor}
                    >
                        {orgUsers.map((u) => (
                            <SelectItem key={u.user_id}>{u.name || u.email || u.user_id}</SelectItem>
                        ))}
                    </Select>
                )}

                <Select
                    isClearable
                    variant="bordered"
                    className="w-full sm:w-1/2 lg:w-48 xl:w-64"
                    placeholder="Filter by Status"
                    startContent={<Filter size={18} className="text-zinc-500" />}
                    selectedKeys={selectedStatus}
                    onSelectionChange={setSelectedStatus}
                >
                    <SelectItem key="Approved">Approved</SelectItem>
                    <SelectItem key="In Review">In Review</SelectItem>
                    <SelectItem key="Draft">Draft</SelectItem>
                    <SelectItem key="Rejected">Rejected</SelectItem>
                </Select>
            </div>
            
        </div>
    );
}